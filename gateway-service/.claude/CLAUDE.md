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

No custom Gradle tasks are defined. No Dockerfile exists in this service yet.

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

A single `RouterFunction<ServerResponse>` bean (`gatewayRouterFunctions`) statically proxies:

| Path pattern | Methods | Downstream |
|---|---|---|
| `/payments/**` | GET, POST | `http://localhost:8080` (payment-service) |
| `/merchants/**` | GET, POST | `http://localhost:8082` (merchant-service) |
| `/webhooks/**` | GET, POST | `http://localhost:8083` (webhook-service) |
| `/settlements/**` | GET, POST | `http://localhost:8084` (settlement-service) |

Important gaps to know before extending this:
- **`mock-bank-service` (8081) intentionally has no route** — it's internal-only, called by payment-service, never exposed externally. Don't add a public route for it without a reason.
- Only GET/POST are routed, even though downstream services may support PUT/PATCH/DELETE.
- Targets are **hardcoded `localhost:<port>`** — this only works when the gateway runs on the same host as the other services outside Docker. It is not container-network-aware (no service-name-based URIs, no config-driven/env-driven targets, no service discovery). If this gateway is ever run inside `docker-compose.yml`, these URIs will need to become the Docker service names, or the gateway route config will need to move to `application.yml`/env vars.
- No filters attached to routes: no retry, no circuit breaker, no path rewrite/strip-prefix, no auth/API-key filter, no rate limiting.

## Configuration (`application.yml`)

```yaml
server:
  port: 9000
spring:
  application:
    name: api-gateway
management:
  endpoints:
    web:
      exposure:
        include: gateway, health
```

Only one profile exists (no `application-dev.yml`, no `application-prod.yml`). Only `gateway` and `health` actuator endpoints are exposed — no `prometheus`/`metrics`/`info`.

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
- If you change routing to be config-driven, prefer YAML route definitions or env-var-driven targets over hardcoding, since this is the one piece most likely to break outside a single-host dev setup.
- There is no shared/parent Gradle build across services (see root CLAUDE.md) — this module's `build.gradle` is fully self-contained; add dependencies here directly.
