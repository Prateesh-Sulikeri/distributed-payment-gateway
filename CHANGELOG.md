# Changelog

Notable changes to the system as a whole. Per-service detail lives in each service's `.claude/PENDING.md` under a dated entry; this file is the cross-service summary.

## 2026-07-04 — Stage 4: Infra & Config Refactor

Production-readiness pass across all six services, run in parallel: standardized configuration, removed dead/hardcoded config, containerized every service, and made Kafka topic provisioning explicit. No business logic, authentication, or resilience behavior changed — this was scoped to infra and config only (see `.claude/ROADMAP.md` Stage 4 for what's next).

### Added
- Multi-stage `Dockerfile` for all six services (Gradle build stage → slim `eclipse-temurin` JRE runtime, non-root user, `/actuator/health`-based `HEALTHCHECK`).
- `docker-compose.yml` now runs the full system: all six services added as compose services alongside the existing infra containers, wired via Docker service-name DNS, gated by `depends_on` healthcheck conditions instead of plain start-order.
- `scripts/provision_kafka_topics.py` — reusable Kafka topic provisioner (Docker-and-AWS-portable), plus a one-shot `kafka-topic-init` Compose service that runs it automatically. Its image pins `python:3.11-slim`, not 3.12 — `kafka-python==2.0.2` fails to import under 3.12 (`ModuleNotFoundError: kafka.vendor.six.moves`), found during end-to-end Docker verification.
- Dual Kafka listeners (`kafka:29092` internal, `localhost:9092` host) so containerized and non-containerized services can both reach the same broker.
- Healthchecks on every Postgres container, Redis, and Kafka.
- `.env.example` for every service (previously only `payment-service` had one, and it was stale).
- `application-dev.yml` / `application-sit.yml` / `application-prod.yml` per service, selected via `SPRING_PROFILES_ACTIVE`.
- Actuator (`health`, `info`, `metrics`, `prometheus`-placeholder) + graceful shutdown on every service.
- `DEPLOYMENT.md` — environment variable reference and Docker Compose operating instructions.
- gateway-service's first-ever `.env`/`.env.example`.
- webhook-service: `V2__fix_webhook_retry_index.sql`, correcting the `idx_webhook_retry` partial index to also cover `FAILED` rows.

### Changed
- **Environment variable naming standardized system-wide**: `DB_URL`/service-prefixed DB vars (`MERCHANT_DB_URL`, `WEBHOOK_DB_URL`, ...) replaced with a consistent `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` shape in every service; `MERCHANT_PORT`/`WEBHOOK_PORT`/hardcoded settlement port replaced with `SERVER_PORT` everywhere.
- All inter-service Feign URLs (payment-service, webhook-service → merchant-service) and gateway-service's four proxied route targets are now `${ENV_VAR:http://localhost:<port>}`-driven instead of hardcoded Java string literals.
- `spring.jpa.hibernate.ddl-auto` standardized to `validate` in payment-service and settlement-service (both were `none`); merchant-service and webhook-service already used `validate`.
- mock-bank-service: fixed a `spring.application.name` YAML indentation bug that silently prevented the property from ever binding; `BankConfig` is now actually wired into `CardProcessor`/`UpiProcessor`/`NetBankingProcessor` (per-method `success-rate`/`min-latency-ms`/`max-latency-ms` are genuinely configurable now, not hardcoded constants — default behavior unchanged).

### Removed
- Dead JWT/legacy-auth configuration: `jwt.*`, `auth.user.*` deleted from payment-service and merchant-service (`.env` + `application.yml`). merchant-service's `AUTH_ADMIN_*` was deliberately **kept**, unused, reserved for a future admin-auth phase — see `DEPLOYMENT.md`.
- payment-service's dead `mock_bank.url` config (no HTTP client to mock-bank-service ever existed; the integration is Kafka-only).
- merchant-service's unused `spring-boot-starter-kafka` and `spring-security-crypto` Gradle dependencies (confirmed via grep — no Kafka code, hashing is hand-rolled `MessageDigest`).
- A dozen-plus dead `.env` variables across mock-bank-service, webhook-service, and settlement-service that no code ever read (topic-name and retry-tuning vars whose real values were hardcoded Java literals; settlement-service's unused `REDIS_HOST`/`REDIS_PORT`).
- mock-bank-service's dead code: `BankPaymentMethod` enum, `FailureReason.ACCOUNT_BLOCKED`.

### Verified end-to-end
`docker compose up -d --build` was run to a fully healthy state (all 13 containers) and a real payment was driven through `gateway-service` (`POST /payments` → `202` → async `SUCCESS`), confirming every container-to-container hop actually works over Docker networking: gateway→payment-service, payment-service→merchant-service (Feign), payment-service↔Kafka, mock-bank-service↔Kafka, webhook-service's independent fan-out consumption + Feign lookup + delivery-retry scheduling, and settlement-service's independent fan-out consumption. This is not just "each service's own `./gradlew build` passed" — it's the full cross-service flow, live, in containers.

### Explicitly not done in this pass
No new authentication, rate limiting, circuit breakers, tracing, or Prometheus registry wiring — those remain future-phase work per `.claude/ROADMAP.md` Stages 3 and 6. `ApiKeyValidationFilter`'s known fail-open gap was left untouched by design.
