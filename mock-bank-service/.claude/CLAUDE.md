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
config/    KafkaConsumerConfig, KafkaProducerConfig, BankConfig (unused, see Known Gaps)
controller/ BankPaymentController — POST /bank/process (synchronous test entry point)
dto/       BankPaymentRequest, BankPaymentResponse
event/     PaymentInitiatedEvent (in), PaymentProcessedEvent (out),
           PaymentInitiatedConsumer, PaymentProcessedProducer
processor/ PaymentProcessor (interface), AbstractPaymentProcessor (shared helpers),
           CardProcessor / UpiProcessor / NetBankingProcessor, PaymentRouter
service/   BankPaymentService (interface) + impl — single orchestration point used by
           BOTH the REST controller and the Kafka consumer
type/      PaymentMethod, CurrencyCode, TransactionStatus, FailureReason
           (+ BankPaymentMethod — dead duplicate, see Known Gaps)
```

`PaymentRouter` is a Strategy-pattern dispatcher: it holds an immutable `Map<PaymentMethod, PaymentProcessor>` built at construction from the three `@Component` processors, and `getProcessor()` throws `IllegalArgumentException` for unsupported methods. This is the extension point for adding a new payment method — add a new `PaymentProcessor` `@Component` and it's picked up automatically by the map-building constructor.

## Simulation behavior (hardcoded per-processor constants, NOT read from config)

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
- Topic names are **hardcoded string literals** in code (`"payment.initiated"`, `"payment.processed"`), not read from `.env`'s `KAFKA_PAYMENT_INITIATED_TOPIC`/`KAFKA_PAYMENT_PROCESSED_TOPIC` (those vars are defined but unused).

## REST endpoint

`POST /bank/process` (`BankPaymentController`) — accepts `BankPaymentRequest`, delegates to the exact same `BankPaymentService` the Kafka consumer uses. Useful for manual/Swagger testing without going through Kafka. Documented via springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) — Swagger UI/OpenAPI JSON available at the default paths.

## Configuration (`application.yml`, single profile, no dev/prod split)

```yaml
server.port: 8081
spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
bank.success-rate: 0.8       # UNUSED (see Known Gaps)
bank.min-latency-ms: 100     # UNUSED
bank.max-latency-ms: 500     # UNUSED
```
Logging: file at `logs/mock-bank-service.log`, daily rolling (10MB/30-day history).

## Known gaps / dead code (verified — don't assume these work)

- **`spring.application.name` is not actually set.** `application.yml` has:
  ```yaml
  spring:
    application:
    name: mock-bank-service
  ```
  `name:` is indented as a *sibling* of `application:`, not nested under it (missing 2 spaces) — so `spring.application.name` is unset, and `spring.name`/top-level `name` (not a real Spring property) is what's actually parsed. Fix by indenting `name:` two more spaces if this ever needs to be correct.
- **`BankConfig` (`@ConfigurationProperties(prefix="bank")`) is never injected anywhere.** The `bank.success-rate`/`min-latency-ms`/`max-latency-ms` YAML values do nothing — real values are hardcoded `private static final` constants inside `CardProcessor`/`UpiProcessor`/`NetBankingProcessor`. If asked to make simulation behavior configurable, this is the class to wire up.
- **`BankPaymentMethod` enum is an unused duplicate of `PaymentMethod`** — dead code, safe to remove if cleaning up.
- **`FailureReason.ACCOUNT_BLOCKED`** is defined but never produced by any processor.
- **Resilience4j is a declared dependency only** (`resilience4j-circuitbreaker`, `-retry`, `-timelimiter`, `spring-cloud-starter-circuitbreaker-resilience4j`) — no `@CircuitBreaker`/`@Retry`/`@RateLimiter` annotation exists anywhere in this service. The only real resilience mechanism here is Kafka's `@RetryableTopic`. Root `PROGRESS.md` still lists "circuit breaker on bank calls" / "rate limiter around Mock Bank" as TODOs — if implementing these, they were planned to wrap *calls into* this service (from payment-service) or possibly within it; check current state before assuming either.
- **No idempotency check on the consumer** — `PaymentInitiatedEvent.idempotencyKey` is deserialized but never read. Redelivery of a Kafka message would re-run the simulation and publish a second `payment.processed` event for the same `paymentId`. (Downstream, payment-service is expected to be idempotent on consuming `payment.processed` — see its CLAUDE.md.)
- **Only one test exists**, `MockBankServiceApplicationTests`, and it's `@Disabled`. No coverage of processors, router, consumer, or producer.
