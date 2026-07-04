# PENDING.md — merchant-service

Tracks outstanding work for this service, consolidated from `PROGRESS.md`, the original roadmap PDF (`distributed_payment_gateway_roadmap(11).pdf`), and direct code analysis. See `.claude/CLAUDE.md` (this service and root) for architecture context behind each item.

## From PROGRESS.md

- **Multiple API keys per merchant support** (Phase 3 cleanup, unchecked) — schema and entity only support one active `apiKeyHash` column; rotating a key immediately invalidates the previous one with no overlap/grace period.
- **Merchant status management (ACTIVE/INACTIVE/SUSPENDED enforcement)** (Phase 3 cleanup, unchecked) — `MerchantStatus` is set to `ACTIVE` at registration and never read again. `MerchantServiceImpl.validateApiKey` only checks the hash match, not status — a `SUSPENDED`/`INACTIVE` merchant's key still validates successfully today.
- **Error handling improvements** (Phase 3 cleanup, "General", unchecked) — concretely in this service: `getMerchantById`/`updateMerchant`/`rotateApiKey` throw bare `RuntimeException`/`IllegalArgumentException` with no `@ControllerAdvice`, so "not found"/"bad id" surface as generic `500`s instead of `404`/`400`.
- **API documentation (Swagger/OpenAPI on all endpoints)** (Phase 3 cleanup + Phase 7 checklist, unchecked) — **not started here**: no springdoc/OpenAPI dependency or annotations exist in this service at all (unlike payment-service/mock-bank-service).

## From the roadmap PDF

- **"API Key auth — every payment request must include a valid merchant API key"** (Phase 3 goal) — the validation endpoint (`GET /merchants/validate-key`) exists and is consumed correctly by payment-service, but the roadmap's implicit expectation of a properly access-controlled merchant management surface is not met (see holistic gap below — merchant-service's *own* endpoints have no auth at all).
- **Phase 7 — Swagger on all services** — this is the service furthest behind on that specific item; no OpenAPI dependency exists yet.
- **Phase 7 — architecture diagram / design decisions documentation** — system-wide item; this service's merchant-registration and API-key lifecycle would be a natural candidate for a sequence diagram once the README's architecture diagram is built.

## Holistic gaps (missed by both PROGRESS.md and the roadmap)

- **merchant-service's own endpoints have no authentication or authorization at all** — no Spring Security dependency, no filter chain, no admin credential check anywhere. Anyone with network access to port 8082 can register merchants, fetch any merchant's details by ID (including their `webhookUrl`), or rotate any merchant's API key (which immediately invalidates the legitimate merchant's key). This is arguably the single biggest security gap in the whole system and isn't called out anywhere in `PROGRESS.md` or the roadmap, which only discuss API-key auth *for payment requests*, not for the merchant-management surface itself.
- **No `@Valid` enforcement anywhere** — `MerchantRequest`'s `@AssertTrue isValid()` Bean Validation constraint is dead code because the controller never annotates the request body with `@Valid`.
- **Dead dependencies**: `spring-boot-starter-kafka` is declared but no producer/consumer code exists anywhere in this service (no merchant-lifecycle events are published). `spring-security-crypto` is declared but unused — API key hashing is hand-rolled with `java.security.MessageDigest` instead.
- **Dead `.env` config**: `JWT_SECRET`, `JWT_EXPIRATION`, `AUTH_USER_*`, `AUTH_ADMIN_*` are defined but read by nothing in the codebase.
- **Only one test exists** (`MerchantServiceApplicationTests`, empty `contextLoads()`) — despite `-data-jpa-test`, `-flyway-test`, `-webmvc-test` all being present as test dependencies, none are used.
- **API key format has no separator** (`sk_live` + raw UUID hex with no delimiter) — cosmetic, but worth fixing alongside any future key-format work since it makes keys harder to visually parse/redact in logs.

## Additional Improvements (suggested future roadmap)

- **Add authentication to merchant-service's own management endpoints** before this system goes anywhere near a real deployment — at minimum HTTP Basic with the still-unused `AUTH_ADMIN_*` credentials already sitting in `.env`, ideally a proper API-key-for-admins or mTLS-between-services scheme once the gateway exists.
- Enforce `MerchantStatus` in `validateApiKey` — reject `SUSPENDED`/`INACTIVE` merchants explicitly, and add an endpoint (or extend `PUT /{id}`) to actually transition a merchant's status.
- Add a `@ControllerAdvice`/`GlobalExceptionHandler` (mirroring payment-service's pattern) to map not-found/bad-id exceptions to proper `404`/`400` responses.
- Support multiple concurrent API keys per merchant (e.g. a separate `merchant_api_keys` table keyed by merchant, each with its own status/expiry) so rotation can have a grace-period overlap instead of instantly invalidating the old key.
- Publish merchant lifecycle events (`MerchantRegistered`, `MerchantStatusChanged`, `MerchantWebhookUrlUpdated`) to Kafka — this would let `payment-service`/`webhook-service` react to status changes without a synchronous Feign round-trip on every request, and would finally give merchant-service a reason to re-add `spring-boot-starter-kafka` (removed in the 2026-07-04 infra refactor below).
- Add springdoc-openapi and annotate the controller, closing the Swagger gap relative to payment-service/mock-bank-service.
- Add real tests: unit tests for `ApiKeyGenerator` (hash determinism, format), `MerchantServiceImpl` (status enforcement once added, partial-update semantics), and a `@WebMvcTest`/Testcontainers-backed integration test for the controller + Flyway schema.
- Consider a constant-time comparison in `ApiKeyGenerator.verify()` (currently a plain `String.equals`) for defense-in-depth, even though the practical risk is low given API keys are high-entropy.

## 2026-07-04 — Stage 4 Infra & Config Refactor

### Issue
merchant-service's `.env`/`application.yml` used service-prefixed DB var names (`MERCHANT_DB_URL`, `MERCHANT_PORT`), carried dead JWT/basic-auth vars, declared two unused dependencies (`spring-boot-starter-kafka`, `spring-security-crypto`), had no Spring profiles, no `.env.example`, no actuator/health endpoint, and no Dockerfile — all blockers for containerizing the service and deploying it consistently alongside the other five services.

### Root Cause
The service was originally built standalone against a shared local Postgres/Kafka stack with ad hoc env-var naming; production-readiness concerns (profiles, actuator, container image, standardized config keys) were never part of the original phase-by-phase build.

### Solution
- Renamed DB env vars to the unprefixed convention shared across all six services in this refactor: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` (values preserved: `localhost:5433/merchant_db`, `merchant_user`/`merchant_pass`).
- Renamed `MERCHANT_PORT` → `SERVER_PORT`.
- Added `SPRING_PROFILES_ACTIVE=dev` to `.env`.
- Deleted dead vars `JWT_SECRET`, `JWT_EXPIRATION`, `AUTH_USER_USERNAME`, `AUTH_USER_PASSWORD`, `AUTH_USER_ROLE` (confirmed unread by any code — root `application.yml` never had a `jwt:`/`auth:` block).
- **Kept** `AUTH_ADMIN_USERNAME`/`AUTH_ADMIN_PASSWORD`/`AUTH_ADMIN_ROLE` verbatim, with a new comment marking them reserved for future admin auth on the currently-open management endpoints (see "Holistic gaps" above) — explicitly not wired to anything in this pass.
- Removed `spring.kafka.bootstrap-servers` from `application.yml` and `KAFKA_BOOTSTRAP_SERVERS` from `.env`, and removed the `spring-boot-starter-kafka` line from `build.gradle` — confirmed via grep that zero Kafka imports/annotations exist anywhere in `src/`. **This removal is reversible**: the dependency will very likely need to come back verbatim once merchant lifecycle events (`MerchantRegistered`/`MerchantStatusChanged`/`MerchantWebhookUrlUpdated`, already listed above as a future-roadmap item) are implemented — it's a one-line re-add, not a design change.
- Removed `spring-security-crypto` from `build.gradle` — confirmed via grep that no `org.springframework.security.crypto.*` import exists; hashing is 100% hand-rolled `java.security.MessageDigest` in `ApiKeyGenerator`. Also reversible if a future pass moves off hand-rolled SHA-256 to BCrypt/Argon2 (see "Additional Improvements" above re: constant-time comparison).
- Created `.env.example` with placeholder values for every var actually read post-cleanup, including the reserved `AUTH_ADMIN_*` block with its comment.
- Added `spring-boot-starter-actuator` and a `management:`/`info:` block to `application.yml` (health/info/metrics/prometheus exposed, health details always shown in dev, graceful shutdown with a 30s timeout-per-shutdown-phase).
- Added `application-dev.yml` (DEBUG root logging), `application-sit.yml` (INFO), `application-prod.yml` (WARN root logging + `management.endpoint.health.show-details: never`).
- Added a multi-stage `Dockerfile` (Gradle build stage on `eclipse-temurin:21-jdk-alpine`, non-root runtime on `eclipse-temurin:21-jre-alpine`, `/actuator/health` HEALTHCHECK).
- Left `spring.jpa.hibernate.ddl-auto: validate` untouched (already correct — Flyway owns the schema).
- No Spring Security filter chain, `@Valid` wiring, `MerchantStatus` enforcement, exception handling, or multi-API-key support was touched — all explicitly out of scope for this pass.

### Files Modified
- `merchant-service/.env` — rewritten (renamed vars, dropped dead JWT/user-auth vars, kept+commented `AUTH_ADMIN_*`, added `SPRING_PROFILES_ACTIVE`)
- `merchant-service/.env.example` — new
- `merchant-service/src/main/resources/application.yml` — datasource url/user/pass now built from `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`; `server.port` from `SERVER_PORT`; removed `spring.kafka.*`; added `management:`/`info:` blocks, `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase`
- `merchant-service/src/main/resources/application-dev.yml` — new
- `merchant-service/src/main/resources/application-sit.yml` — new
- `merchant-service/src/main/resources/application-prod.yml` — new
- `merchant-service/Dockerfile` — new, multi-stage build
- `merchant-service/build.gradle` — added `spring-boot-starter-actuator`; removed `spring-boot-starter-kafka` and `spring-security-crypto`
- `merchant-service/.claude/CLAUDE.md` — Configuration and Known-gaps/dead-dependency sections updated to reflect new env scheme and what was removed vs. reserved

### Impact
- **Runtime**: No behavior change to any endpoint — this is pure config/infra plumbing. `spring.datasource.url` resolves to the identical JDBC URL as before, just assembled from five vars instead of one.
- **Deployment**: Service is now containerizable (Dockerfile added) and its `.env` follows the same unprefixed convention as the other five services, simplifying orchestration (docker-compose/ECS task defs) that inject per-service env files.
- **Performance**: Negligible — one fewer autoconfigured Kafka producer/consumer bean graph at startup since the starter is gone; startup should be marginally faster.
- **Security**: `/actuator` endpoints are now exposed (health/info/metrics/prometheus) with no auth in front of them, same as the rest of this service's surface today — this is a known, pre-existing gap (see "merchant-service's own endpoints have no authentication" above), not a new one introduced by this change. `prod` profile at least hides health details (`show-details: never`).
- **Maintainability**: Fewer unused dependencies to reason about; `.env.example` gives new contributors a template that didn't exist before; profile split makes log verbosity/health exposure intent explicit per environment instead of implicit/undocumented.

### Follow-up
- Re-add `spring-boot-starter-kafka` when merchant lifecycle events are implemented (see "Additional Improvements" above) — do not treat its removal here as a decision against ever using Kafka in this service.
- Once admin auth is implemented, lock down `/actuator/**` (at minimum `/actuator/env`, `/actuator/metrics`) behind it — currently only `health`/`info`/`metrics`/`prometheus` are exposed, but none are gated.
- `docker-compose.yml` wiring for this service's container (build context, env-file, port mapping) is root-level/coordinator work, not done here.
