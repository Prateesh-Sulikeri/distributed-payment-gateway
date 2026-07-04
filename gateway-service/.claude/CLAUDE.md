# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Service overview

`gateway-service` is the planned single entry point for the distributed payment gateway (Spring Cloud Gateway, port **9000**, `spring.application.name: api-gateway`). It is **early-stage / mostly scaffolding** (Phase 7, branch `phase/7-polish`, "In Progress") — do not assume features described in the root README/PROGRESS.md ("Phase 7") are implemented here just because they're planned. See the repo-root `.claude/CLAUDE.md` for the system-wide picture; this file documents only what actually exists in this module today.

## Commands

```bash
./gradlew build       # compile + test + package
./gradlew test        # run tests (JUnit 5 / JUnit Platform)
./gradlew bootRun      # run locally on port 9000
```

No custom Gradle tasks are defined. A multi-stage `Dockerfile` exists (see Configuration section below) but `docker-compose.yml` has no service entry for it yet.

## Current implementation state (as of this writing)

The entire `src/main` tree is 4 files:

```
src/main/java/com/payment/gateway_service/
├── GatewayServiceApplication.java   # bare @SpringBootApplication, nothing customized
└── config/
    └── GatewayRoutes.java           # the only real logic — static route table
src/main/resources/application.yml   # port + actuator exposure only
```

Note the base package is `com.payment.gateway_service` (underscore) — `gateway-service` is not a valid Java package segment, per the module's `HELP.md`.

`src/test` has exactly one file: `GatewayServiceApplicationTests.java` with an empty `contextLoads()` — no routing/behavior tests exist.

## Build setup

- Spring Boot **4.0.6**, Spring Cloud BOM **2025.1.1**, Java 21 toolchain.
- Dependency is `spring-cloud-starter-gateway-server-webmvc` — the **WebMVC (servlet) flavor** of Spring Cloud Gateway, not the reactive WebFlux gateway. Keep this in mind if adding filters/routes: use the WebMVC functional DSL (`GatewayRouterFunctions`, `HandlerFunctions`, `BeforeFilterFunctions`), not `RouteLocatorBuilder`/reactive `GatewayFilter`.
- Also present: `spring-boot-starter-actuator`, `spring-boot-starter-validation`.
- **Not present** (and therefore not usable without adding them first): Micrometer tracing/Zipkin, `micrometer-registry-prometheus`, any Redis/rate-limiter starter, springdoc/OpenAPI.

## Routing (`config/GatewayRoutes.java`)

A single `RouterFunction<ServerResponse>` bean (`gatewayRouterFunctions`) proxies:

| Path pattern | Methods | Downstream | Target property |
|---|---|---|---|
| `/payments/**` | GET, POST | payment-service | `payment-service.url` (default `http://localhost:8080`) |
| `/merchants/**` | GET, POST | merchant-service | `merchant-service.url` (default `http://localhost:8082`) |
| `/webhooks/**` | GET, POST | webhook-service | `webhook-service.url` (default `http://localhost:8083`) |
| `/settlements/**` | GET, POST | settlement-service | `settlement-service.url` (default `http://localhost:8084`) |

**Targets are now config-driven, not hardcoded.** `GatewayRoutes` takes a constructor with 4 `String` params bound via `@Value("${<service>.url}")`, stored as fields, and referenced inside each route's `.before(uri(...))` call. The actual values live in `application.yml` as `${ENV_VAR:default}` placeholders (e.g. `payment-service.url: ${PAYMENT_SERVICE_URL:http://localhost:8080}`), overridable per-environment via env vars — see this service's `.env`/`.env.example`. This was deliberately kept as plain property injection (not `spring.cloud.gateway.routes` declarative YAML, which is the reactive-gateway convention) to stay a minimal diff on top of the existing WebMVC functional DSL, while making a future Eureka/Config-Server migration (`lb://service-name` URIs) a change to only the 4 property values, not the route-building code.

Important gaps to know before extending this:
- **`mock-bank-service` (8081) intentionally has no route** — it's internal-only, called by payment-service, never exposed externally. Don't add a public route for it without a reason.
- Only GET/POST are routed, even though downstream services may support PUT/PATCH/DELETE.
- No filters attached to routes: no retry, no circuit breaker, no path rewrite/strip-prefix, no auth/API-key filter, no rate limiting.

## Configuration (`application.yml`)

```yaml
server:
  port: ${SERVER_PORT:9000}
  shutdown: graceful

spring:
  application:
    name: api-gateway
  lifecycle:
    timeout-per-shutdown-phase: 30s

payment-service:
  url: ${PAYMENT_SERVICE_URL:http://localhost:8080}
merchant-service:
  url: ${MERCHANT_SERVICE_URL:http://localhost:8082}
webhook-service:
  url: ${WEBHOOK_SERVICE_URL:http://localhost:8083}
settlement-service:
  url: ${SETTLEMENT_SERVICE_URL:http://localhost:8084}

management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus, gateway
  endpoint:
    health:
      show-details: always
  info:
    env:
      enabled: true

info:
  app:
    name: gateway-service
    description: Single entry point / reverse proxy for the payment gateway system
```

Three profiles now exist: `application-dev.yml` (`logging.level.root: DEBUG`), `application-sit.yml` (`logging.level.root: INFO`), `application-prod.yml` (`logging.level.root: WARN`, `management.endpoint.health.show-details: never`). Active profile is set via `SPRING_PROFILES_ACTIVE` (see `.env`). `health`, `info`, `metrics`, `prometheus`, and `gateway` actuator endpoints are exposed — note `prometheus` will 404 until the `micrometer-registry-prometheus` dependency is actually added (still not present in `build.gradle`), so exposing it in config alone doesn't make the endpoint functional yet.

This service now has its own `.env`/`.env.example` (first time for this service) providing `SERVER_PORT`, `SPRING_PROFILES_ACTIVE`, and the four downstream `*_SERVICE_URL` overrides. A multi-stage `Dockerfile` also exists (`eclipse-temurin:21-jdk-alpine` build → `eclipse-temurin:21-jre-alpine` runtime, non-root `spring` user, `/actuator/health`-based `HEALTHCHECK`), though `docker-compose.yml` still has no service definition to run it (root-level, out of scope for this service).

## What's explicitly NOT implemented yet (planned per root PROGRESS.md Phase 7 checklist)

When asked to work on any of these, treat it as greenfield work, not a bug fix:
- Distributed tracing (Micrometer + Zipkin) — no dependency, no config, no trace propagation.
- Prometheus metrics export — no dependency, endpoint not exposed.
- Grafana dashboards.
- API-key rate limiting on routes — no Redis-backed `RequestRateLimiter`, no Resilience4j/Bucket4j.
- Swagger/OpenAPI aggregation of downstream services' specs.
- Security/auth filter on the gateway itself (auth today, if any, happens per-downstream-service, e.g. merchant API key checks in payment-service).
- Any filters at all beyond the plain `uri()` forward.

## Working in this service

- If you add tracing, note none of the other services have it wired in either yet (verify against each service's own CLAUDE.md before assuming trace headers will propagate anywhere).
- Routing targets are config-driven (see Routing section above) — when adding a new downstream route, follow the same pattern (`@Value`-injected property with an `${ENV_VAR:default}` in `application.yml`), don't reintroduce a hardcoded `localhost:<port>` literal.
- There is no shared/parent Gradle build across services (see root CLAUDE.md) — this module's `build.gradle` is fully self-contained; add dependencies here directly.
