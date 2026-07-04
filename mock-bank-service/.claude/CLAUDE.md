# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Service overview

`mock-bank-service` simulates a bank/card-network processor for the distributed payment gateway. It has no real banking logic — it exists purely so the rest of the system can exercise async payment processing without depending on a real bank integration (see repo-root design doc reasoning: "as a production system doesn't care about the internal workings of a real bank"). Port **8081**. Stateless — no database.

See the repo-root `.claude/CLAUDE.md` for the system-wide event flow and other services.

## Commands

```bash
./gradlew build
./gradlew test
./gradlew test --tests "com.payments.mock_bank_service.MockBankServiceApplicationTests"
./gradlew bootRun    # requires Kafka reachable at KAFKA_BOOTSTRAP_SERVERS (default localhost:9092)
```

Base package: `com.payments.mock_bank_service`.

## Architecture

```
config/    KafkaConsumerConfig, KafkaProducerConfig, BankConfig (nested per-method success-rate/latency, injected into the three processors)
controller/ BankPaymentController — POST /bank/process (synchronous test entry point)
dto/       BankPaymentRequest, BankPaymentResponse
event/     PaymentInitiatedEvent (in), PaymentProcessedEvent (out),
           PaymentInitiatedConsumer, PaymentProcessedProducer
processor/ PaymentProcessor (interface), AbstractPaymentProcessor (shared helpers),
           CardProcessor / UpiProcessor / NetBankingProcessor, PaymentRouter
service/   BankPaymentService (interface) + impl — single orchestration point used by
           BOTH the REST controller and the Kafka consumer
type/      PaymentMethod, CurrencyCode, TransactionStatus, FailureReason
```

`PaymentRouter` is a Strategy-pattern dispatcher: it holds an immutable `Map<PaymentMethod, PaymentProcessor>` built at construction from the three `@Component` processors, and `getProcessor()` throws `IllegalArgumentException` for unsupported methods. This is the extension point for adding a new payment method — add a new `PaymentProcessor` `@Component` and it's picked up automatically by the map-building constructor.

## Simulation behavior (defaults; now genuinely configurable via `bank.*` YAML/env, see Configuration below)

| Method | Success rate | Latency | Failure reasons (uniform random) |
|---|---|---|---|
| CARD | 85% | 200–500ms | `INSUFFICIENT_FUNDS`, `CARD_EXPIRED`, `CVV_MISMATCH` |
| UPI | 92% | 50–150ms | `UPI_ID_NOT_FOUND`, `DAILY_LIMIT_EXCEEDED` |
| NET_BANKING | 78% | 1000–3000ms | `BANK_TIMEOUT`, `SESSION_EXPIRED` |

- `AbstractPaymentProcessor.simulateLatency()` does a real **blocking `Thread.sleep`** for the randomized duration — under Kafka listener concurrency this occupies a consumer thread for the full latency, it isn't async.
- `isApproved()` is a single independent `ThreadLocalRandom.nextDouble() < successRate` check per request — no stateful streaks, no per-account/per-card behavior.
- All randomness funnels through the shared `AbstractPaymentProcessor` helpers (`simulateLatency`, `isApproved`, `randomFailureReason`) — the one place to swap in deterministic/seeded randomness for tests.

## Kafka

- **Consumes** `payment.initiated` — `PaymentInitiatedConsumer`, group `mock-bank-group`.
  `@RetryableTopic(attempts="3", backOff=@BackOff(delay=2000, multiplier=2.0), dltTopicSuffix=".DLT", topicSuffixingStrategy=SUFFIX_WITH_INDEX_VALUE, autoStartDltHandler="false")`
  → 3 attempts (~2s/4s/8s backoff) → indexed retry topics → `payment.initiated.DLT`.
  **No `@DltHandler` exists anywhere** — DLT messages land there unconsumed today; there's no dead-letter monitor for this service.
- **Produces** `payment.processed` via `PaymentProcessedProducer`, keyed by `paymentId.toString()` (preserves per-payment ordering).
- Serialization: `JacksonJsonDeserializer`/`JacksonJsonSerializer`, producer sets `setAddTypeInfo(false)` (no `__TypeId__` header — consumers must deserialize against their own DTO shape). Consumer trusts all packages (`addTrustedPackages("*")`).
- Flow: consumer builds a `BankPaymentRequest` from the inbound event → `BankPaymentService.process()` → builds `PaymentProcessedEvent` (copying `merchantId` from the **original inbound event**, not the response) → publishes.
- Topic names are **hardcoded string literals** in code (`"payment.initiated"`, `"payment.processed"`), not config-driven. `.env` used to carry unread `KAFKA_PAYMENT_INITIATED_TOPIC`/`KAFKA_PAYMENT_PROCESSED_TOPIC` vars for these; they were removed as dead config in the 2026-07-04 Stage 4 refactor (see Known Gaps) since nothing read them.

## REST endpoint

`POST /bank/process` (`BankPaymentController`) — accepts `BankPaymentRequest`, delegates to the exact same `BankPaymentService` the Kafka consumer uses. Useful for manual/Swagger testing without going through Kafka. Documented via springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) — Swagger UI/OpenAPI JSON available at the default paths.

## Configuration (`application.yml` + `application-{dev,sit,prod}.yml` profiles)

```yaml
server.port: ${SERVER_PORT:8081}
spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
bank.card.success-rate: 0.85
bank.card.min-latency-ms: 200
bank.card.max-latency-ms: 500
bank.upi.success-rate: 0.92
bank.upi.min-latency-ms: 50
bank.upi.max-latency-ms: 150
bank.net-banking.success-rate: 0.78
bank.net-banking.min-latency-ms: 1000
bank.net-banking.max-latency-ms: 3000
```
`bank.*` is bound via `BankConfig` (`@ConfigurationProperties(prefix = "bank")`, nested
`card`/`upi`/`netBanking` groups) and injected into `CardProcessor`/`UpiProcessor`/
`NetBankingProcessor` — these are now the real source of success-rate/latency, not
hardcoded constants (see Known Gaps for history). Active profile is set via
`SPRING_PROFILES_ACTIVE` (`.env`, defaults to `dev` there); `application-{dev,sit,prod}.yml`
only vary logging verbosity and, for `prod`, `management.endpoint.health.show-details`.
`management.endpoints.web.exposure.include` covers `health,info,metrics,prometheus`
(Actuator was already a `build.gradle` dependency).
Logging: file at `logs/mock-bank-service.log`, daily rolling (10MB/30-day history).

## Known gaps / dead code (verified — don't assume these work)

- ~~`spring.application.name` is not actually set~~ — **fixed** (2026-07-04 Stage 4 refactor). `name:` is now correctly nested under `spring.application` in `application.yml`.
- ~~`BankConfig` is never injected anywhere~~ — **fixed** (2026-07-04). `BankConfig` was restructured into nested `card`/`upi`/`netBanking` groups and is now constructor-injected into `CardProcessor`/`UpiProcessor`/`NetBankingProcessor`, which read `successRate`/`minLatencyMs`/`maxLatencyMs` from it instead of hardcoded constants. Default YAML values reproduce the prior hardcoded behavior exactly (Card 85%/200-500ms, UPI 92%/50-150ms, NetBanking 78%/1000-3000ms).
- ~~`BankPaymentMethod` enum is an unused duplicate of `PaymentMethod`~~ — **removed** (2026-07-04).
- ~~`FailureReason.ACCOUNT_BLOCKED` defined but never produced~~ — **removed** (2026-07-04).
- **Kafka topic names (`.env`'s old `KAFKA_PAYMENT_INITIATED_TOPIC`/`KAFKA_PAYMENT_PROCESSED_TOPIC`) were unread dead vars and have been removed from `.env`/`.env.example`** — topic names remain hardcoded string literals in `PaymentInitiatedConsumer`/`PaymentProcessedProducer`; making them config-driven was deferred as a larger change (touches `@KafkaListener`/producer call sites) — still open.
- **Resilience4j is a declared dependency only** (`resilience4j-circuitbreaker`, `-retry`, `-timelimiter`, `spring-cloud-starter-circuitbreaker-resilience4j`) — no `@CircuitBreaker`/`@Retry`/`@RateLimiter` annotation exists anywhere in this service. The only real resilience mechanism here is Kafka's `@RetryableTopic`. Root `PROGRESS.md` still lists "circuit breaker on bank calls" / "rate limiter around Mock Bank" as TODOs — if implementing these, they were planned to wrap *calls into* this service (from payment-service) or possibly within it; check current state before assuming either.
- **No idempotency check on the consumer** — `PaymentInitiatedEvent.idempotencyKey` is deserialized but never read. Redelivery of a Kafka message would re-run the simulation and publish a second `payment.processed` event for the same `paymentId`. (Downstream, payment-service is expected to be idempotent on consuming `payment.processed` — see its CLAUDE.md.)
- **Only one test exists**, `MockBankServiceApplicationTests`, and it's `@Disabled`. No coverage of processors, router, consumer, or producer.
