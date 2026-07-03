# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Service overview

`payment-service` is the core of the distributed payment gateway — it accepts payment requests, persists them, and drives them through async processing via Kafka. Port **8080**, own Postgres (`payment_db`, host port 5432), Redis cache, Kafka producer+consumer. It is the system's busiest integration point: talks to `merchant-service` (sync, Feign) and `mock-bank-service` (async, Kafka only).

See the repo-root `.claude/CLAUDE.md` for the system-wide event flow and other services.

## Commands

```bash
./gradlew build
./gradlew test
./gradlew test --tests "com.payments.payment_service.PaymentServiceApplicationTests"
./gradlew bootRun   # needs payment_db (5432), Redis (6379), Kafka (9092), merchant-service (8082) reachable
```

Base package: `com.payments.payment_service`. Entry point is `@SpringBootApplication @EnableFeignClients @EnableScheduling`.

**Spring Boot 4 / Jackson 3 note**: this service mixes Jackson packages — `PaymentResponse` imports both classic `com.fasterxml.jackson.annotation.JsonPropertyOrder` and Jackson 3's `tools.jackson.databind.annotation.*`. `PaymentServiceImpl`, `OutboxPublisherJob`, and `RedisConfig` use `tools.jackson.databind.ObjectMapper`/`JsonMapper`, not the classic `com.fasterxml.jackson.databind.ObjectMapper`. Mind which package is in scope when touching serialization code.

## Architecture

```
common/
  config/    KafkaConsumerConfig, KafkaProducerConfig, RedisConfig
  exception/ ApiError, GlobalExceptionHandler (@RestControllerAdvice), PaymentNotFoundException
  lock/      DistributedLock (entity), LockRepository, LockService — hand-rolled DB-row mutex, NOT ShedLock/Redisson
  type/      CurrencyCode, EventStatus, EventType, FailureReason, MerchantStatus, PaymentMethod,
             PaymentStatus, TransactionStatus
payment/
  cache/     PaymentCacheService — Redis cache-aside, fail-open on Redis errors
  client/    MerchantServiceClient (Feign, hardcoded http://localhost:8082) + dto/MerchantResponse
  controller/ PaymentController — @RequestMapping("/payments")
  dto/       PaymentRequest, PaymentResponse
  entity/    Payment — table `payments`
  event/     PaymentEventProducer (→ payment.initiated), PaymentInitiatedEvent,
             PaymentProcessedConsumer (← payment.processed), PaymentProcessedEvent,
             PaymentSucceededProducer (→ payment.succeeded), PaymentSucceededEvent (record)
             event/outbox/  OutboxEvent (entity), OutboxPublisherJob (@Scheduled 5s), OutboxRepository
  filter/    ApiKeyValidationFilter — OncePerRequestFilter, the actual auth mechanism (see Security)
  mapper/    PaymentMapper — manual, no MapStruct
  repository/ PaymentRepository
  service/   PaymentService (interface) + impl/PaymentServiceImpl
```

**No `PaymentProcessor`/`PaymentRouter` strategy-pattern classes exist in this service** — that Card/UPI/NetBanking simulation logic lives entirely in `mock-bank-service`. `PaymentMethod` is just an enum here, independently duplicated (not shared) between the two services. Don't look for bank-simulation logic in payment-service.

## Domain model — `Payment` entity (table `payments`)

```
id             UUID, @GeneratedValue(UUID)
idempotencyKey String, not null, UNIQUE
amount         BigDecimal(precision=19, scale=4), not null
currency       CurrencyCode enum (STRING), not null (INR/USD/JPY/AUD)
status         PaymentStatus enum (STRING), not null (PENDING/SUCCESS/FAILED)
paymentMethod  PaymentMethod enum (STRING), not null (UPI/CARD/NET_BANKING)
description    String, nullable, 500 chars
failureReason  String, nullable, 500 chars — stored as FailureReason.name(), not an enum column
merchantId     UUID, not null
createdAt      Instant, @CreationTimestamp
updatedAt      Instant, @UpdateTimestamp
```
No local `Merchant` entity — `merchantId` is an opaque UUID; merchant details are fetched transiently via Feign, never persisted locally.

Flyway migrations (`db/migration/`): V1 creates `payments`; V2 adds `merchant_id` (initially VARCHAR with a zero-UUID default); V3 creates `outbox_events`; V4 creates `distributed_locks`; V5 converts `merchant_id` from VARCHAR to native UUID.

Controllers/DTOs are annotated with springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) — Swagger UI is available at the default `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`.

## API endpoints (`PaymentController`, base `/payments`)

| Method | Path | Notes |
|---|---|---|
| `POST` | `/payments` | Header `Idempotency-Key` required. Body `PaymentRequest` (`@Valid`). Returns **`202 Accepted`** + `PaymentResponse` (Swagger doc string says "created or retrieved" but code returns 202, not 200). `merchantId` comes from a request attribute set by `ApiKeyValidationFilter` — **can be null** (see Security gap below). |
| `GET` | `/payments/admin?page=&size=` | Not capped in the controller, but `PaymentServiceImpl.getAllPayments` caps it internally at 100 (`Math.min(size,100)`) — same effective limit as the status endpoint below, just enforced one layer down. |
| `GET` | `/payments/admin/id/{id}` | Cache-first lookup; 404 via `PaymentNotFoundException` if absent. |
| `GET` | `/payments/admin/status/{status}?page=&size=` | size capped at 100 via `Math.min(size, 100)`. |

**No Spring Security / `@PreAuthorize`** — the only gate is the plain servlet `ApiKeyValidationFilter`, and it does **not** distinguish `/payments` (create) from `/payments/admin/**` (read) — both get the same optional check.

## Business logic (`PaymentServiceImpl.createPayment`)

1. Validate `idempotencyKey` non-blank (else `IllegalArgumentException` → 400).
2. **Cache-aside**: check Redis (`PaymentCacheService.getByIdempotencyKey`) first.
3. On cache miss, fall through to `paymentRepository.findByIdempotencyKey` — if found, re-populate cache and return (idempotent replay, no new row).
4. Otherwise: build `Payment(status=PENDING)`, `saveAndFlush` (flushed immediately so a unique-constraint violation surfaces synchronously), build a `PaymentInitiatedEvent`, serialize it, and write an `OutboxEvent(PAYMENT_INITIATED, PENDING)` **in the same transaction** as the payment insert — this is the transactional-outbox write. Cache the response by both idempotency key and id.
5. **Race guard**: if `saveAndFlush` throws `DataIntegrityViolationException` (concurrent duplicate insert for the same idempotency key), catch it and re-fetch the existing row instead of failing. Note: this same catch block also (mis)catches other constraint violations — e.g. a null `merchant_id` — as if they were duplicate-key races (see Security gap below for the resulting confusing failure mode).

`getPaymentById`/`getAllPayments`/`getPaymentByStatus` follow the same Redis-first, DB-fallback pattern via `PaymentCacheService`. An `evict()` method on the cache service exists but **is never called anywhere** — the Kafka consumer that updates payment status does not evict/refresh the cache, so a cached `PaymentResponse` can go stale relative to DB status until re-fetched fresh or the 24h TTL expires.

## Transactional outbox pattern

`Payment` insert + `OutboxEvent` insert happen atomically in `createPayment`'s single transaction. The actual Kafka publish is decoupled: `OutboxPublisherJob` (`@Scheduled(fixedRate=5000)`, `@Transactional`) acquires `LockService.acquireLock("outbox-publisher", 30)` (a hand-rolled Postgres-row-based mutex — **not** ShedLock/Redisson, see `common/lock`) before polling `PENDING` outbox rows. For each `PAYMENT_INITIATED` row, deserializes the JSON payload, calls `paymentEventProducer.publishPaymentInitiated(event).get()` (blocks on the Kafka send future). Success → row marked `SUCCESS`. Any exception → row is **left as `PENDING`** (not `FAILED`) for infinite retry on the next 5s tick — no backoff, no max-attempts counter for this specific job.

**Known gap** (open TODO in root `PROGRESS.md`): outbox publishing is not fully idempotent — if the job crashes after the Kafka ack but before the DB status commit, the event could be double-published.

`EventType.PAYMENT_PROCESSED` exists as an enum value but is **never produced into the outbox** by this service — that branch in the publisher is dead code.

## Distributed lock (`common/lock`)

Hand-rolled, not a library: `LockService.acquireLock(name, durationSeconds)` tries to `INSERT` a `DistributedLock` row (name = PK); a `DataIntegrityViolationException` means the lock is already held — if the existing row's `expiresAt` has passed, delete it and retry the insert (lock-stealing from a crashed holder). `releaseLock` just deletes by id. Only used by `OutboxPublisherJob` (`"outbox-publisher"`, 30s TTL vs. 5s poll interval). Note `lockedBy` is a hardcoded literal `"payment-service-instance"`, not a real per-instance identifier.

## Kafka

**Produces:**
- `payment.initiated` — `PaymentEventProducer.publishPaymentInitiated`, key=`paymentId`. `@CircuitBreaker(name="kafkaProducer", fallbackMethod="publishToDeadLetterFallback")`. Only ever invoked from `OutboxPublisherJob`, never directly from the service layer.
- `payment.succeeded` — `PaymentSucceededProducer.publishPaymentSucceeded`, key=`paymentId`. Same circuit breaker instance/pattern. Invoked from `PaymentProcessedConsumer` on `APPROVED`. **No outbox durability for this one** — if the circuit breaker/producer fails, the event is just logged and lost (unlike `payment.initiated`).
- Both fallback methods are named `publishToDeadLetterFallback` but **do not actually write to any dead-letter store** — they just log and return a failed future. Don't assume a DLT record exists for these failures.

**Consumes:**
- `payment.processed` (from `mock-bank-service`) — `PaymentProcessedConsumer`, group **`payment-group`**. `@RetryableTopic(attempts="3", backOff(delay=2000, multiplier=2.0), dltTopicSuffix=".DLT", topicSuffixingStrategy=SUFFIX_WITH_INDEX_VALUE, autoStartDltHandler="false")`. **No `@DltHandler` exists** — `payment.processed.DLT` messages accumulate unconsumed. Root `PROGRESS.md`'s planned `GET /admin/dlt-messages` was never built.
- **Idempotency guard**: only applies the update if `payment.getStatus() == PENDING` — duplicate/replayed `payment.processed` messages (Kafka is at-least-once) are logged and ignored once the payment has already transitioned. On `APPROVED` → `SUCCESS` + publish `payment.succeeded`; on `DECLINED` → `FAILED` + `failureReason` set from the event.

**Serialization**: `JacksonJsonSerializer`/`JacksonJsonDeserializer` (Spring Kafka, Jackson-3-backed), producer sets `setAddTypeInfo(false)` (no `__TypeId__` header), consumer trusts all packages (`addTrustedPackages("*")`).

`docker-compose.yml` sets `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"` — topics must already exist; no `NewTopic` bean/provisioning script exists in this service.

## Resilience4j

Only **circuit breaker** is actually used, despite retry/timelimiter also being on the classpath:
```yaml
resilience4j.circuitbreaker.instances.kafkaProducer:
  slidingWindowSize: 5
  failureRateThreshold: 50.0
  waitDurationInOpenState: 10000
  permittedNumberOfCallsInHalfOpenState: 3
  automaticTransitionFromOpenToHalfOpenEnabled: true
```
Applied via `@CircuitBreaker(name="kafkaProducer", ...)` on both Kafka producer methods. No `@Retry`/`@TimeLimiter`/`@RateLimiter` anywhere — deliberate design choice per root `PROGRESS.md`'s Phase 5 notes: the outbox (DB durability + 5s retry-forever) plus circuit breaker is considered sufficient, and `@TimeLimiter` was explicitly rejected because it needs async return types incompatible with the outbox job's `void` flow. Kafka **consumer** resilience is handled by `@RetryableTopic` (Spring Kafka, not Resilience4j) — durable retry topics survive app crashes, unlike in-memory `@Retry`.

Kafka producer native timeouts (separate from Resilience4j): `request.timeout.ms=3000`, `delivery.timeout.ms=5000`, `max.block.ms=3000`.

No resilience annotation wraps the Feign call to merchant-service in `ApiKeyValidationFilter` — only a Feign-level timeout (`connectTimeout`/`readTimeout` = 5000ms via `spring.cloud.openfeign.client.config.merchant-service`). If merchant-service is slow, every request through the filter blocks up to that timeout with no fallback.

## Security — the most important gap to know about

**No Spring Security dependency at all.** `application.yml` declares `jwt.secret`, `jwt.expiration`, `auth.user.*`, `auth.admin.*` (env-backed) but **grep confirms zero code references them anywhere** — this is dead config left over from Phase 1 (JWT auth, per root `PROGRESS.md`) that was superseded by API-key validation in Phase 3 and never cleaned up. Do not assume JWT auth is active.

The real mechanism is `ApiKeyValidationFilter` (`OncePerRequestFilter`, `@Component`, auto-registered for all paths):
- Only inspects requests where the URI starts with `/payments`.
- **If the `Authorization` header is absent or doesn't start with `"Bearer "`, the filter calls `filterChain.doFilter()` and lets the request through unauthenticated** — `merchantId` is simply never set as a request attribute.
- If present, extracts the token, calls `MerchantServiceClient.validateApiKey(apiKey)` (Feign → `GET http://localhost:8082/merchants/validate-key`). Empty result → `401 "Invalid API Key"`. Success → sets `merchantId`/`merchant` request attributes.

**Concrete failure mode**: `POST /payments` with **no** `Authorization` header at all does not get rejected by the filter — it proceeds with `merchantId=null`, `PaymentServiceImpl.createPayment` tries to persist a `Payment` with a `NOT NULL merchant_id` column, throws `DataIntegrityViolationException`, which the idempotency race-guard catch block **misdiagnoses as a duplicate-key race**, triggers a `findByIdempotencyKey` lookup that returns empty, and ultimately surfaces as a confusing generic **500** rather than a clean 401. If you're asked to harden auth, this is the first thing to fix — the filter should reject requests with no `Authorization` header, not silently pass them through.

The `/payments/admin/**` endpoints get **no distinct authorization** from the create endpoint — same filter, same optional check, no admin-vs-merchant role distinction despite the `/admin` path naming.

`MerchantServiceClient` uses a **hardcoded** `http://localhost:8082` URL (no service discovery, no env var) — will need to change for any non-localhost deployment.

## Configuration (`application.yml`, single profile — no dev/prod split)

```yaml
server.port: 8080
spring.datasource: ${DB_URL}/${DB_USERNAME}/${DB_PASSWORD}
spring.jpa: ddl-auto=none (Flyway owns schema), PostgreSQLDialect
spring.data.redis: ${REDIS_HOST}:${REDIS_PORT}, timeout=2000ms
spring.flyway: baseline-on-migrate=true
spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.cloud.openfeign.client.config.merchant-service: connectTimeout=5000, readTimeout=5000
jwt.secret / jwt.expiration / auth.user.* / auth.admin.*   # DEAD — unread by any code
mock_bank.url: ${MOCK_BANK_URL}                             # DEAD — no HTTP client to mock-bank exists; integration is Kafka-only
logging.file.name: logs/payment-service.log
logging.level: com.payments.payment_service.payment.event.outbox=WARN (quiets the 5s poll)
resilience4j.circuitbreaker.instances.kafkaProducer: (see above)
```

`.env.example` (checked in) is **out of sync** — it only lists `DB_URL/USERNAME/PASSWORD`, `REDIS_HOST/PORT`, `JWT_SECRET`, `JWT_EXPIRATION`, missing `AUTH_*`, `KAFKA_*`, `MOCK_BANK_URL` that `application.yml`/the real `.env` reference (some of those, like `MOCK_BANK_URL`, aren't even set in the real `.env` either — but nothing reads it, so it's harmless). `KAFKA_PAYMENT_INITIATED_TOPIC`/`KAFKA_PAYMENT_PROCESSED_TOPIC` env vars exist in the real `.env` but aren't referenced anywhere — topic names are hardcoded string literals in the producer/consumer classes.

## Known gaps (verified — don't assume these work)

- **No active tests.** The only test class, `PaymentServiceApplicationTests`, is `@Disabled`. No unit/integration/Testcontainers coverage exists for any controller, service, repository, mapper, cache, or Kafka logic.
- Dead config: `jwt.*`, `auth.user.*`, `auth.admin.*`, `mock_bank.url`.
- Auth filter silently passes through requests with no `Authorization` header (see Security).
- Outbox publish is not fully idempotent (crash-window double-publish risk).
- `payment.succeeded` has no outbox/durability guarantee, unlike `payment.initiated`.
- `.DLT` topics have no consumer/monitor; planned `GET /admin/dlt-messages` was never built.
- `GlobalExceptionHandler`'s catch-all `Exception` handler returns the raw exception message in the 500 body — leaks internal details to API clients.
- `PaymentCacheService.evict()` is unused — cached responses can go stale relative to DB status after a Kafka-driven status update.
- Hardcoded Feign URL to merchant-service (`http://localhost:8082`) — not environment-configurable.
