# PENDING.md — gateway-service

Tracks outstanding work for this service, consolidated from `PROGRESS.md`, the original roadmap PDF (`distributed_payment_gateway_roadmap(11).pdf`), and direct code analysis. See `.claude/CLAUDE.md` (this service and root) for architecture context behind each item.

This service is the least mature in the system (Phase 7, "In Progress") — most of its checklist is genuinely greenfield work, not bug-fixing.

## From PROGRESS.md

- **Spring Cloud Gateway wired** (Phase 7 checklist, unchecked at time of writing, now partially true) — a basic static `RouterFunction` bean with 4 routes (`/payments/**`, `/merchants/**`, `/webhooks/**`, `/settlements/**` → hardcoded `localhost` ports) exists, plus `gateway`/`health` actuator endpoints. This is a minimal working proxy, not the full "single entry point" the roadmap describes (no auth, no rate limiting, no resilience, no dynamic routing).
- **API key rate limiting added** (Phase 7 checklist, unchecked) — no rate limiter dependency, no Redis-backed `RequestRateLimiter`, no filter code, no config.
- **Tracing enabled across services** (Phase 7 checklist, unchecked) — no Micrometer/Zipkin dependency or config in this service (or any other service in the system).
- **Prometheus actuator metrics exposed** (Phase 7 checklist, unchecked) — no `micrometer-registry-prometheus` dependency; `prometheus` isn't in `management.endpoints.web.exposure.include` (only `gateway`, `health` are exposed).
- **Grafana dashboard documented** (Phase 7 checklist, unchecked) — no dashboard config/docs anywhere in the repo.
- **Swagger/OpenAPI added** (Phase 7 checklist, unchecked) — no springdoc dependency; also no OpenAPI aggregation of the downstream services' own Swagger docs (a gateway-level aggregation is what the roadmap implies for "Swagger on all services").
- **Architecture diagram / design decisions documented** (Phase 7 checklist, unchecked) — root-level items, but the gateway's routing table would be a natural centerpiece of that diagram once built.
- **Bruno request collection committed / Grafana screenshot** (Phase 7 checklist, unchecked) — root-level items; no Bruno collection exists in the repo yet.

## From the roadmap PDF

- **"Single entry point for all client traffic. It handles routing to the right service, rate limiting per API key, and authentication."** — routing exists; rate limiting and **authentication at the gateway** do not. Today, each downstream service (only payment-service, really) does its own API-key check — the gateway itself performs no auth, so a client could bypass the intended "one gate" model by calling a downstream service's port directly if it's network-reachable.
- **"100 requests/minute per merchant"** (Phase 7 checklist detail) — the specific rate-limiting figure the roadmap targets; useful as a concrete default once rate limiting is implemented.
- **Phase 7 — Micrometer + Zipkin tracing, Prometheus + Grafana dashboard (payments/sec, success rate, p99 latency, circuit breaker state, Kafka consumer lag)** — none of this exists yet; the gateway is the natural place for request-level tracing to originate (assigning/propagating the trace ID) once added.

## Holistic gaps (missed by both PROGRESS.md and the roadmap)

- **Routes are hardcoded to `localhost:<port>`** — this only works when the gateway and all downstream services run on the same host outside Docker. It is not container-network-aware (no service-name-based URIs, no env-driven targets, no service discovery). If this gateway is ever run inside `docker-compose.yml` (which today has no app-container definitions at all — see root `PENDING.md`), these URIs would need to become Docker service names or config-driven values.
- **Only GET/POST are routed** — even though downstream services likely support PUT/PATCH/DELETE on some paths (e.g. `merchant-service`'s `PUT /merchants/{id}`), those aren't reachable through the gateway at all today.
- **`mock-bank-service` has no route, intentionally** — worth stating explicitly so a future change doesn't accidentally expose it: it's meant to stay internal-only, reachable only via Kafka from `payment-service`.
- **No filters at all beyond the plain `uri()` forward** — no retry, no circuit breaker, no path rewriting/strip-prefix, no request/response logging, at the gateway layer. If a downstream service is down, the client just gets whatever raw connection-refused/timeout error bubbles up.
- **Only a trivial context-load test exists** — no test exercises actual routing behavior (no `WebTestClient`/`MockMvc`/WireMock-backed test verifying a request to `/payments/**` actually proxies to the right place).

## Additional Improvements (suggested future roadmap)

- Move route targets to `application.yml` (or env-var-driven values) instead of hardcoding `localhost:<port>` in Java — this is the single highest-leverage fix before any containerized/multi-host deployment.
- Add the API-key rate limiter the roadmap specifies (100 req/min per merchant is a reasonable starting default) — Resilience4j's `RateLimiter` or a Redis-backed `RequestRateLimiter` (Spring Cloud Gateway's built-in filter) are both viable; Redis is already in the stack for payment-service's cache.
- Add authentication at the gateway itself (delegating to merchant-service's `/validate-key`, same as payment-service does today) so the "one gate" model is actually enforced, rather than relying on each downstream service to independently re-implement the check.
- Add a circuit breaker per route (Resilience4j's Spring Cloud Gateway integration) so a downed downstream service degrades gracefully instead of leaking raw connection errors to clients.
- Extend routing to cover PUT/PATCH/DELETE for the downstream paths that support them.
- Once tracing is added system-wide, make sure the gateway is where the trace ID originates/is guaranteed present on every proxied request, since it's the single entry point.
- Add OpenAPI aggregation (or at minimum a documentation page linking to each downstream service's own `/v3/api-docs`) once Swagger exists on all six services.
- Add integration tests that actually verify routing (e.g. WireMock stand-ins for the four downstream services, asserting the right target receives the forwarded request).

## 2026-07-04 — Stage 4 Infra & Config Refactor

### Issue
`gateway-service` had no `.env`/`.env.example`, its route targets were hardcoded `localhost:<port>` string literals baked into `GatewayRoutes.java`, `server.port` was a fixed literal, only two actuator endpoints (`gateway`, `health`) were exposed, there were no environment-specific Spring profiles, and no Dockerfile existed — all blockers for containerizing this service or running it against non-localhost downstream services (e.g. in Docker Compose or AWS).

### Root Cause
The service was built as early-stage Phase 7 scaffolding (single-host dev setup only) with no config-driven deployment concerns addressed yet — this is the first infra/config pass it has received.

### Solution
- Refactored `GatewayRoutes` to take a 4-argument constructor (`paymentServiceUrl`, `merchantServiceUrl`, `webhookServiceUrl`, `settlementServiceUrl`), each bound via `@Value("${<service>.url}")`, and used those fields inside `.before(uri(...))` instead of string literals. Kept the existing WebMVC functional route-building DSL as-is (no rewrite to declarative `spring.cloud.gateway.routes` YAML, which is the reactive-gateway convention).
- Added `payment-service.url`, `merchant-service.url`, `webhook-service.url`, `settlement-service.url` properties to `application.yml`, each `${ENV_VAR:default}`-driven so behavior is unchanged out-of-the-box.
- Made `server.port` env-driven (`${SERVER_PORT:9000}`), added `server.shutdown: graceful` and `spring.lifecycle.timeout-per-shutdown-phase: 30s`.
- Expanded actuator exposure to `health, info, metrics, prometheus, gateway`, set `management.endpoint.health.show-details: always` (dev-time default) and enabled `management.info.env.enabled`, plus static `info.app.*` metadata.
- Added `application-dev.yml` (DEBUG root logging), `application-sit.yml` (INFO), `application-prod.yml` (WARN root logging, `health.show-details: never` to avoid leaking details in prod).
- Created this service's first-ever `.env` and `.env.example` (identical content, no real secrets) and added a `.env`-ignoring / `.env.example`-allowing block to `.gitignore` (previously `.env` files were not gitignored at all in this service).
- Added a multi-stage `Dockerfile` (`eclipse-temurin:21-jdk-alpine` build stage running `./gradlew bootJar -x test`, `eclipse-temurin:21-jre-alpine` runtime stage, non-root `spring` user, `/actuator/health`-based `HEALTHCHECK`).

### Files Modified
- `src/main/java/com/payment/gateway_service/config/GatewayRoutes.java` (constructor + `@Value` fields, replaced literal URIs)
- `src/main/resources/application.yml` (env-driven port, 4 downstream URL properties, expanded actuator/info config, graceful shutdown)
- `src/main/resources/application-dev.yml` (new)
- `src/main/resources/application-sit.yml` (new)
- `src/main/resources/application-prod.yml` (new)
- `.env` (new)
- `.env.example` (new)
- `.gitignore` (added `.env` ignore / `.env.example` allow)
- `Dockerfile` (new)
- `.claude/CLAUDE.md` (Routing and Configuration sections updated to reflect config-driven targets, profiles, Dockerfile, `.env`)

### Impact
- **Runtime**: No behavioral change out-of-the-box — all new properties resolve to the same defaults (`localhost:8080/8082/8083/8084`, port `9000`) when no env vars are set.
- **Deployment**: Route targets, port, and active profile can now be overridden per-environment via env vars or `.env`, and the service can be built into a container image via the new `Dockerfile` — both prerequisites for Docker Compose / AWS deployment. `docker-compose.yml` still has no service entry for this image (root-level, out of scope here).
- **Performance**: Negligible — constructor injection of 4 strings adds no measurable overhead; graceful shutdown adds up to 30s to shutdown time under in-flight requests.
- **Security**: `management.endpoint.health.show-details` is `always` by default (dev) but `never` under the `prod` profile, avoiding leaking internal health details in production. No auth/rate-limiting added (explicitly out of scope for this pass).
- **Maintainability**: New downstream routes now follow a clear, consistent config-driven pattern instead of ad hoc string literals, and profile-specific logging levels make dev/sit/prod log verbosity intentional rather than uniform.

### Follow-up
- `docker-compose.yml` needs a service entry for `gateway-service` before the new `Dockerfile` can actually be exercised in the local stack (root-level file, out of scope for this pass).
- `prometheus` is now listed in `management.endpoints.web.exposure.include` but the `micrometer-registry-prometheus` dependency is still not in `build.gradle` — the endpoint will 404 until that dependency is added.
- Route targets are still plain HTTP URLs, not `lb://service-name` — migrating to Eureka/service-discovery-based URIs later only requires changing the 4 property values, not `GatewayRoutes.java`, per the design intent of this refactor.
- No filters (auth, rate limiting, circuit breaker, retry) were added — still tracked as separate, later-phase items elsewhere in this file.
