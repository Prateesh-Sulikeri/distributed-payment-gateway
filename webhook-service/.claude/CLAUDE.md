# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Service overview

`webhook-service` notifies merchants of payment status changes by POSTing to their registered callback URL, with exponential-backoff retries and a dead-letter state. Port **8083**, own Postgres (`webhook_db`, host port 5434).

See the repo-root `.claude/CLAUDE.md` for the system-wide event flow and other services.

## Commands

```bash
./gradlew build
./gradlew test
./gradlew test --tests "com.payments.webhook_service.WebhookServiceApplicationTests"
./gradlew bootRun   # needs webhook-db (5434), Kafka, and merchant-service (8082) reachable
```

Base package: `com.payments.webhook_service`. Entry point is `@SpringBootApplication @EnableFeignClients @EnableScheduling`.

## Architecture

```
client/    MerchantServiceClient (Feign, hardcoded http://localhost:8082) + dto/MerchantResponse
config/    KafkaConsumerConfig, RestTemplateConfig (5s connect/read timeout)
controller/ WebhookAdminController — @RequestMapping("/webhooks")
dto/       WebhookDeliveryRequest (dead — unused by any code path), WebhookDeliveryResponse
entity/    WebhookDelivery — the single table that plays BOTH outbox-row and delivery-audit-record role
event/     PaymentProcessedConsumer, PaymentProcessedEvent
job/       WebhookRetryJob — @Scheduled polling worker, this IS the outbox drain job
mapper/    WebhookMapper
repository/ WebhookDeliveryRepository
service/   WebhookDeliveryService (+impl) — delivery HTTP call, backoff/failure bookkeeping, admin queries
type/      WebhookDeliveryStatus, FailureReason, MerchantStatus, TransactionStatus
           (the latter three are independently duplicated copies of payment-service's enums — no shared library)
```

## Event flow — `webhook_deliveries` IS the outbox (single-table design)

`PaymentProcessedConsumer` (`@KafkaListener(topics="payment.processed", groupId="webhook-service")`) is a **separate, independent consumer group** from payment-service's own `payment-group` on the same topic — both get their own copy of every message (fan-out, not competing consumers). This topic is produced directly by `mock-bank-service` (no outbox on that side); the *real* transactional outbox pattern in this system lives in payment-service (for `payment.initiated` only), not here.

On receipt:
1. Calls `MerchantServiceClient.getMerchantById(merchantId)` (Feign → `GET http://localhost:8082/merchants/{id}`).
2. If merchant not found or has no `webhookUrl`, logs a warning and **drops the event** — no row created, no retry tracked for "merchant has no webhook configured."
3. Otherwise saves a new `WebhookDelivery` row: `status=PENDING`, `attemptCount=0`, `nextRetryAt=now()`, `payload` = the raw serialized `PaymentProcessedEvent` JSON. This save **is** the outbox write for this subsystem.
4. **All exceptions in the listener are caught and only logged, never rethrown.** This means the `@RetryableTopic` wrapping this listener (`attempts=3`, backoff 2s×2.0, `.DLT` suffix) is **effectively dead/unreachable** — it never fires because the method body never throws. Don't assume Kafka-level retry protects this consumer; the only real protection is the try/catch swallowing errors (which means a transient merchant-service outage during consumption **silently drops the webhook-trigger entirely**, no DB row, no retry).
5. **No idempotency guard** on inbound events — no unique constraint on `payment_id` in `webhook_deliveries`, no dedup check. A duplicate `payment.processed` delivery (Kafka is at-least-once) creates a **second** `WebhookDelivery` row and sends a duplicate webhook to the merchant.

## Retry / backoff — the actual outbox worker is `WebhookRetryJob`

`@Scheduled(fixedRate = 10000)` (every 10s) queries `findByStatusInAndNextRetryAtBefore([PENDING, FAILED], now())` and calls `deliverWebhook()` for each, in a plain loop — **no batching limit, no distributed lock** (contrast with payment-service's `OutboxPublisherJob`, which uses `LockService`). Running multiple instances of this service could double-send webhooks.

Backoff math (`WebhookDeliveryServiceImpl.handleDeliveryFailure`, hardcoded literals, **not read from `.env`** despite `.env` defining matching-sounding vars):
```java
attemptCount++;
if (attemptCount > 5) {
    status = DEAD;
} else {
    status = FAILED;
    nextRetryAt = now + (30 * 2^(attemptCount - 1)) seconds;
}
```
Schedule: attempt 1→30s, 2→60s, 3→120s, 4→240s, 5→480s, then `DEAD`. **`.env`'s `WEBHOOK_MAX_RETRY_ATTEMPTS=5` and `WEBHOOK_INITIAL_RETRY_DELAY_SECONDS=30` are unused dead config** — matching the shipped values by coincidence/original design intent, but changing the `.env` values does nothing; the literals must be edited in code.

There is no distinct `RETRYING` status — a webhook awaiting its backoff window is stored as `FAILED` with a future `nextRetryAt`. Once `DEAD`, `WebhookRetryJob`'s query no longer picks it up; only the manual admin retry endpoint can revive it.

## Delivery mechanism

`WebhookDeliveryServiceImpl.deliverWebhook()` — plain `RestTemplate` (not RestClient/WebClient), 5s connect/read timeout. POSTs the stored `payload` string with headers `Content-Type: application/json`, `X-Webhook-ID: <delivery id>`, `X-Webhook-Timestamp: <epoch ms>`.

**No HMAC/signature header exists** (`X-Webhook-Signature` or similar) — webhook signature verification is explicitly an open TODO in root `PROGRESS.md` ("verify requests came from gateway"). Don't assume merchants can cryptographically verify webhook authenticity today.

2xx → `DELIVERED` + `deliveredAt`. Non-2xx/exception → `handleDeliveryFailure(...)`, then the exception is **rethrown** so the wrapping `@CircuitBreaker(name="webhookDelivery", fallbackMethod="webhookDeliveryFallback")` sees it.

**Resilience4j gap**: `application.yml` configures `resilience4j.retry.instances.webhookDelivery` (maxAttempts 3, 1000ms wait, retry on `SocketTimeoutException`/`ConnectException`/`ResourceAccessException`/`IOException`) and `resilience4j.timelimiter.instances.webhookDelivery` (5s timeout) — but **only `@CircuitBreaker` is actually annotated on `deliverWebhook()`**; `@Retry` and `@TimeLimiter` are imported but never applied. If asked to "make webhook retry more resilient," check whether adding those annotations was the actual intent versus the DB-level backoff already covering it.

Circuit breaker config (`webhookDelivery` instance): `slidingWindowSize=5`, `failureRateThreshold=50.0`, `waitDurationInOpenState=15000`ms, `permittedNumberOfCallsInHalfOpenState=3`. The fallback (`webhookDeliveryFallback`) only logs — when the circuit is OPEN, calls short-circuit *before* `handleDeliveryFailure` runs, so `attemptCount`/`nextRetryAt` are **not** updated; the row is simply retried again on the job's next 10s tick once the breaker allows it.

## REST endpoints (`WebhookAdminController`, base `/webhooks`)

| Method | Path | Behavior |
|---|---|---|
| `GET` | `/status-pending?page=&size=` | Paged `PENDING` deliveries, size capped at 100 |
| `GET` | `/status-dead?page=&size=` | Paged `DEAD` deliveries (dead-letter view) |
| `POST` | `/retry/{id}` | Only allowed from `FAILED`/`DEAD` (else `IllegalStateException`); resets to `PENDING`, `nextRetryAt=now`, then **synchronously** calls `deliverWebhook` (the HTTP response blocks on the outbound webhook attempt); `204` on success |

No create/submit endpoint — `WebhookDeliveryRequest` DTO exists but is unused dead code. No OpenAPI/Swagger annotations anywhere in this service (unlike payment-service/mock-bank-service).

## Data model (Flyway `V1__create_webhook_deliveries_table.sql`)

```
webhook_deliveries: id, merchant_id, payment_id, webhook_url, payload (TEXT), status (PENDING/DELIVERED/FAILED/DEAD),
                     attempt_count, next_retry_at, last_error_message, created_at, updated_at, delivered_at
indexes: idx_webhook_status, idx_webhook_merchant,
         idx_webhook_retry ON (next_retry_at) WHERE status = 'PENDING'   -- partial index does NOT cover FAILED,
                                                                          -- even though the retry job also queries
                                                                          -- FAILED rows — minor index/query mismatch
```
`ddl-auto: validate` — Flyway owns the schema.

## Configuration (`application.yml`, single profile)

```yaml
server.port: ${WEBHOOK_PORT:8083}
spring.datasource: url/username/password from WEBHOOK_DB_URL/WEBHOOK_DB_USERNAME/WEBHOOK_DB_PASSWORD (no defaults — required)
spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
logging.level: com.payments.webhook_service.job=WARN (quiets the 10s poll), root=INFO
resilience4j.circuitbreaker.instances.webhookDelivery: (see above)
resilience4j.retry.instances.webhookDelivery: (configured but unused — see above)
resilience4j.timelimiter.instances.webhookDelivery: (configured but unused — see above)
```
`.env` also defines `KAFKA_PAYMENT_PROCESSED_TOPIC`, `KAFKA_WEBHOOK_TRIGGERED_TOPIC`, `PAYMENT_SERVICE_URL`, `WEBHOOK_MAX_RETRY_ATTEMPTS`, `WEBHOOK_INITIAL_RETRY_DELAY_SECONDS` — **none of these five are referenced anywhere in code** (topic name is a hardcoded string literal, retry constants are hardcoded, and webhook-service never calls payment-service directly). No `.env.example` is checked in for this service.

## Known gaps (don't assume these work)

- No idempotency/dedup on inbound `payment.processed` events — duplicates create duplicate deliveries.
- Kafka-level `@RetryableTopic`/DLT on the consumer is unreachable dead config since the listener swallows all exceptions.
- No distributed lock on `WebhookRetryJob` — unsafe to run multiple instances without adding one.
- `@Retry`/`@TimeLimiter` Resilience4j config exists in YAML but isn't applied in code — only `@CircuitBreaker` is active.
- No webhook signature/HMAC verification — merchants cannot cryptographically verify authenticity.
- Only one test exists (`WebhookServiceApplicationTests`), and it's `@Disabled` — no active test coverage at all.
- Several `.env` variables are entirely unused/misleading (see Configuration section) — don't assume editing them changes behavior.
