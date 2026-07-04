# PENDING.md — webhook-service

Tracks outstanding work for this service, consolidated from `PROGRESS.md`, the original roadmap PDF (`distributed_payment_gateway_roadmap(11).pdf`), and direct code analysis. See `.claude/CLAUDE.md` (this service and root) for architecture context behind each item.

## 2026-07-04 — Stage 4 Infra & Config Refactor

### Issue
Ahead of Docker containerization / AWS deployment, this service's configuration didn't follow the cross-service standard: DB connection env vars used a service-specific naming scheme (`WEBHOOK_DB_URL`/`WEBHOOK_DB_USERNAME`/`WEBHOOK_DB_PASSWORD`) instead of the common `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` shape, the HTTP port var was named `WEBHOOK_PORT` instead of `SERVER_PORT`, five `.env` vars were dead (never read by any code), the merchant-service Feign client URL was a hardcoded literal, there was no `.env.example`, no Actuator/health endpoint, no Spring profiles, and no Dockerfile. Separately, the `idx_webhook_retry` partial index (`WHERE status = 'PENDING'`) didn't match `WebhookRetryJob`'s actual query predicate (`status IN ('PENDING', 'FAILED')`), so the index is not used for the `FAILED` half of every poll.

### Root Cause
Config accreted ad hoc during earlier phases without a cross-service convention; the index was written before the retry job's query was extended to also cover `FAILED` rows, and nobody reconciled the two afterward.

### Solution
- Standardized DB env vars to `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`; `application.yml` now builds the JDBC URL from parts (`jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`).
- Renamed `WEBHOOK_PORT` → `SERVER_PORT` (default `8083` preserved).
- Added `SPRING_PROFILES_ACTIVE=dev` to `.env`.
- Removed 5 dead `.env` vars after confirming via repo-wide grep that none are referenced in code: `KAFKA_PAYMENT_PROCESSED_TOPIC`, `KAFKA_WEBHOOK_TRIGGERED_TOPIC`, `PAYMENT_SERVICE_URL`, `WEBHOOK_MAX_RETRY_ATTEMPTS`, `WEBHOOK_INITIAL_RETRY_DELAY_SECONDS`.
- Externalized the merchant-service Feign URL: `application.yml` now has `merchant-service.url: ${MERCHANT_SERVICE_URL:http://localhost:8082}`, and `MerchantServiceClient` uses `@FeignClient(name = "merchant-service", url = "${merchant-service.url}")`.
- **Index/query mismatch fixed via additive migration** (this repo's established convention — payment-service already ships `V1`...`V5` incrementally rather than editing shipped migrations in place): added `V2__fix_webhook_retry_index.sql`, which drops and recreates `idx_webhook_retry` as `ON webhook_deliveries (next_retry_at) WHERE status IN ('PENDING', 'FAILED')`. This was judged safe to apply now (single environment, no shared/prod deployment yet depending on the old checksum).
- Confirmed `spring.jpa.hibernate.ddl-auto: validate` was already correct; left as-is.
- Added `spring-boot-starter-actuator` dependency; added `management.*`/`info.*` blocks (`health,info,metrics,prometheus` exposed, `show-details: always` by default), `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase: 30s`.
- Added `application-dev.yml` (`root: DEBUG`), `application-sit.yml` (`root: INFO`), `application-prod.yml` (`root: WARN`, `management.endpoint.health.show-details: never`).
- Added a multi-stage `Dockerfile` (Gradle build stage on `eclipse-temurin:21-jdk-alpine`, non-root runtime on `eclipse-temurin:21-jre-alpine`, `/actuator/health` `HEALTHCHECK`).
- Created `.env.example` mirroring the cleaned-up `.env`.

### Files Modified
- `webhook-service/.env` — rewritten with new var names, dead vars removed, `SPRING_PROFILES_ACTIVE` added
- `webhook-service/.env.example` — new file
- `webhook-service/src/main/resources/application.yml` — datasource URL construction, `SERVER_PORT`, `merchant-service.url`, `management`/`info` blocks, `server.shutdown`, `spring.lifecycle`
- `webhook-service/src/main/resources/application-dev.yml` — new file
- `webhook-service/src/main/resources/application-sit.yml` — new file
- `webhook-service/src/main/resources/application-prod.yml` — new file
- `webhook-service/src/main/java/com/payments/webhook_service/client/MerchantServiceClient.java` — `@FeignClient` url now `${merchant-service.url}`
- `webhook-service/src/main/resources/db/migration/V2__fix_webhook_retry_index.sql` — new file, fixes the partial index
- `webhook-service/build.gradle` — added `spring-boot-starter-actuator`
- `webhook-service/Dockerfile` — new file
- `webhook-service/.claude/CLAUDE.md` — Configuration section updated

### Impact
- Runtime: no behavior change to webhook delivery, retry/backoff math, Kafka consumption, or the admin endpoints — this was purely config/infra plumbing. The `FAILED`-status retry poll should see a marginal query-plan improvement now that the partial index covers it.
- Deployment: service is now containerizable (Dockerfile) and its env-var naming lines up with the other five services' `DB_HOST`/`DB_PORT`/... convention, easing future orchestration (docker-compose/ECS) and per-environment profile activation (`SPRING_PROFILES_ACTIVE=dev|sit|prod`).
- Performance: negligible — the index fix only helps the retry job's `FAILED` branch of its `WHERE status IN (...)` scan.
- Security: `management.endpoint.health.show-details` defaults to `always` in dev but is locked to `never` in the `prod` profile, avoiding leaking internal health details in a production-like environment. No change to auth (this service has none to begin with; it only receives internal Kafka events).
- Maintainability: dead `.env` vars removed, `.env.example` now exists so new contributors can see the real required config surface, and the merchant-service base URL is no longer a hardcoded Java literal.

### Follow-up
- All previously known gaps in this service (no idempotency guard on inbound events, no distributed lock on `WebhookRetryJob`, `@Retry`/`@TimeLimiter` configured-but-unused, no HMAC signing, dead `@RetryableTopic`) are unchanged by this pass — they are explicitly out of scope per the Stage 4 instructions and remain tracked in the sections below.
- Consider adding `micrometer-registry-prometheus` in a later pass if the `/actuator/prometheus` endpoint needs to actually serve scrapeable metrics (today `spring-boot-starter-actuator` alone exposes `/actuator/metrics` but not the Prometheus text format without that registry dependency).

## From PROGRESS.md

- **Webhook signature verification** (Phase 3 cleanup, unchecked — "for security, verify requests came from gateway") — no HMAC/signature header exists on outbound deliveries; only `X-Webhook-ID` and `X-Webhook-Timestamp` are sent. Merchants have no way to cryptographically verify a webhook actually came from this system.
- **Webhook delivery metrics/monitoring** (Phase 3 cleanup, unchecked — "track success rates, latency") — no metrics are emitted anywhere in this service beyond log lines; `GET /webhooks/status-pending` / `status-dead` give a manual snapshot but there's no time-series/rate tracking.
- **Error handling improvements** (Phase 3 cleanup, "General", unchecked) — concretely here: the Kafka consumer (`PaymentProcessedConsumer`) catches and only logs every exception, silently dropping the webhook-trigger event with no record and no retry (see holistic gap below).
- **API documentation (Swagger/OpenAPI on all endpoints)** (Phase 3 cleanup + Phase 7 checklist, unchecked) — **not started here**: no springdoc dependency or annotations anywhere in this service.

## From the roadmap PDF

- **Retry strategy mismatch**: the roadmap specifies attempt 1 immediate, then 30s / 5min / 30min / 2hr, DEAD after attempt 5. The shipped implementation instead uses `30s × 2^(attempt-1)`: 30s, 60s, 120s, 240s, 480s, then DEAD — i.e. every retry happens within ~8 minutes total instead of spreading over ~2h38m as originally planned. Not necessarily wrong, but it's an unacknowledged deviation from the documented design — worth a conscious decision (and a `DECISIONS.md` entry) on which schedule is actually wanted for a "real" payment gateway (merchant servers down for maintenance for an hour would exhaust all 5 attempts in under 10 minutes today).
- **Dead Letter Queue / DLT monitor endpoint** (Phase 5 concept, system-wide) — this service's `payment.processed.DLT` (from its own `@RetryableTopic`) has no consumer or monitor, same gap as elsewhere — compounded here by the fact the retry-topic mechanism is effectively unreachable anyway (see holistic gap below), so nothing ever reaches the DLT through the intended path.
- **Phase 7 — Swagger, tracing, Prometheus** — none present in this service yet.

## Holistic gaps (missed by both PROGRESS.md and the roadmap)

- **`@RetryableTopic` on `PaymentProcessedConsumer` is dead in practice** — the listener method catches all exceptions internally and never rethrows, so Kafka's retry-topic/DLT machinery (3 attempts, exponential backoff, `.DLT` suffix) never actually triggers. A transient failure while calling merchant-service (e.g. it's briefly down) during event consumption **silently drops the webhook trigger entirely** — no `WebhookDelivery` row is ever created, no retry, no trace beyond a log line.
- **No idempotency/dedup on inbound events** — no unique constraint on `payment_id` in `webhook_deliveries`, no dedup check in the consumer. A duplicate `payment.processed` delivery (Kafka is at-least-once) creates a second `WebhookDelivery` row and sends the merchant a duplicate webhook.
- **No distributed lock on `WebhookRetryJob`** — unlike payment-service's `OutboxPublisherJob` (which uses `LockService`), this service's retry poller has no protection against double-delivery if more than one instance is ever run.
- **Resilience4j `@Retry`/`@TimeLimiter` are configured in YAML but never annotated in code** — only `@CircuitBreaker` is actually applied to `deliverWebhook()`. The `resilience4j.retry.instances.webhookDelivery` and `resilience4j.timelimiter.instances.webhookDelivery` blocks in `application.yml` do nothing today.
- **Several `.env` variables are entirely unused**: `KAFKA_PAYMENT_PROCESSED_TOPIC`, `KAFKA_WEBHOOK_TRIGGERED_TOPIC`, `PAYMENT_SERVICE_URL`, `WEBHOOK_MAX_RETRY_ATTEMPTS`, `WEBHOOK_INITIAL_RETRY_DELAY_SECONDS` — the real topic name and retry constants are hardcoded Java literals, so editing these env vars silently does nothing.
- **The partial index `idx_webhook_retry` only covers `status='PENDING'`**, but `WebhookRetryJob`'s query also selects `FAILED` rows due for retry — the index doesn't match the actual query predicate.
- **`WebhookDeliveryRequest` DTO is entirely unused/dead code** — no controller or service path constructs or consumes it.
- **Only one test exists and it's `@Disabled`** — zero coverage of the backoff math, delivery success/failure paths, or the admin retry endpoint.

## Additional Improvements (suggested future roadmap)

- Let the consumer's exceptions actually propagate for genuine failures (e.g. deserialization errors) while still catching *expected* recoverable conditions (merchant not found / no webhook URL) explicitly and recording them — right now the blanket catch masks both cases identically, which is also why the `@RetryableTopic` machinery never fires.
- Add a unique constraint (or a dedup check like payment-service's idempotency pattern) on `payment_id` before inserting a `WebhookDelivery`, or accept intentional at-least-once delivery but document that merchants must handle duplicate webhooks idempotently (and say so in any future public API docs).
- Add the missing distributed lock to `WebhookRetryJob` before ever running more than one instance of this service.
- Either wire up `@Retry`/`@TimeLimiter` to match the existing YAML config, or delete the unused config blocks — the mismatch is actively misleading to anyone reading `application.yml` expecting those to be active.
- Implement HMAC-SHA256 webhook signing (`X-Webhook-Signature` header, keyed by a per-merchant secret) so merchants can verify authenticity — this was explicitly called out in `PROGRESS.md` and is a normal expectation for any real webhook system (Stripe/Razorpay both do this).
- Emit basic delivery metrics (Micrometer counters for delivered/failed/dead, a timer for delivery latency) ahead of the full Phase 7 Prometheus push — cheap to add now, and directly answers the still-open "webhook delivery metrics/monitoring" TODO.
- Reconsider (and document in `DECISIONS.md`) the backoff schedule — either restore the originally-planned 30s/5min/30min/2hr spread, or consciously keep the tighter exponential schedule and explain why.
- Add real tests for `WebhookDeliveryServiceImpl.handleDeliveryFailure` (backoff math, DEAD transition at attempt 6) and the admin retry endpoint's state-transition guard.
