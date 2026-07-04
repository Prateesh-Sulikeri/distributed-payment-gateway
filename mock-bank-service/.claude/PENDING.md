# PENDING.md — mock-bank-service

Tracks outstanding work for this service, consolidated from `PROGRESS.md`, the original roadmap PDF (`distributed_payment_gateway_roadmap(11).pdf`), and direct code analysis. See `.claude/CLAUDE.md` (this service and root) for architecture context behind each item.

## From PROGRESS.md

- **API documentation (Swagger/OpenAPI on all endpoints)** (Phase 3 cleanup, "General", unchecked) — already satisfied here (`springdoc-openapi-starter-webmvc-ui`, `BankPaymentController` is annotated); tracked because the umbrella item is still open system-wide (see root summary).
- **Error handling improvements** (Phase 3 cleanup, "General", unchecked) — no specific gap called out for this service in `PROGRESS.md`, but see the idempotency and DLT gaps below, which are the concrete instances of this for mock-bank-service.
- **`GET /admin/dlt-messages`** (Phase 5 checklist, unchecked) — this service's `payment.initiated.DLT` topic (from its own `@RetryableTopic` on `PaymentInitiatedConsumer`) has no consumer or monitoring, same gap as every other service in the system.

## From the roadmap PDF

- **Configurable success rate via `application.yaml`** (Phase 1 checklist: "Configurable success rate — Set in application.yaml, e.g. `bank.successRate=0.8`") — **the roadmap's own bar for this was not maintained.** `application.yml` still has `bank.success-rate`, `bank.min-latency-ms`, `bank.max-latency-ms`, and a `BankConfig` `@ConfigurationProperties` class exists to bind them — but `BankConfig` is never injected anywhere. The actual success rates/latencies (`CardProcessor` 85%/200-500ms, `UpiProcessor` 92%/50-150ms, `NetBankingProcessor` 78%/1000-3000ms) are hardcoded `private static final` constants per processor. This is a regression from the Phase 1 design, introduced when the Phase 4 Strategy Pattern refactor moved the simulation logic into per-method processor classes.
- **Rate Limiter** — one of the four resilience patterns the roadmap calls out explicitly "to prevent overloading Mock Bank." Never implemented; Resilience4j is a declared dependency (`resilience4j-circuitbreaker`, `-retry`, `-timelimiter`, plus the Spring Cloud starter) but **zero annotations exist anywhere in this service** — not circuit breaker, not retry, not rate limiter, not bulkhead.
- **Bulkhead pattern** — listed under Phase 5's "Concepts you will learn" alongside circuit breaker/retry/timeout/DLT; never implemented (not even attempted elsewhere in the system).
- **Phase 7 — Swagger (done here), tracing, Prometheus metrics** — tracing and Prometheus are not present in this service (no `micrometer-tracing`/`micrometer-registry-prometheus` dependency).

## Holistic gaps (missed by both PROGRESS.md and the roadmap)

- **`spring.application.name` is silently unset** — `application.yml` has `name: mock-bank-service` indented as a sibling of `application:` instead of nested under it (a 2-space indentation bug), so the property never actually binds.
- **No idempotency check on the Kafka consumer** despite the data being available — `PaymentInitiatedEvent.idempotencyKey` is deserialized but never read by `PaymentInitiatedConsumer`. A redelivered/duplicate `payment.initiated` message (Kafka is at-least-once) re-runs the bank simulation and publishes a **second** `payment.processed` event for the same payment.
- **Dead code accumulating**: `BankPaymentMethod` enum (unused duplicate of `PaymentMethod`), `FailureReason.ACCOUNT_BLOCKED` (defined, never produced by any processor), the unused `BankConfig` import in `BankPaymentServiceImpl`.
- **No `@DltHandler`** — `autoStartDltHandler="false"` on `PaymentInitiatedConsumer` and no handler method defined means `payment.initiated.DLT` messages are silently unconsumed.
- **`simulateLatency` blocks the Kafka listener thread** (`Thread.sleep`) rather than using any async/non-blocking delay — for `NetBankingProcessor`'s up-to-3-second latency, this ties up a consumer thread for the full duration; under load or a low `concurrency` setting, this could create consumer lag that's easy to misdiagnose as "Kafka is slow" rather than "the mock bank simulation is blocking."
- **Only one test exists and it's `@Disabled`** (`MockBankServiceApplicationTests`) — zero coverage of `PaymentRouter`, the three processors' success-rate/latency behavior, or the Kafka consumer/producer.
- **No topic-provisioning** — this service depends on `payment.initiated` and `payment.processed` existing already; nothing here creates them.

## Additional Improvements (suggested future roadmap)

- Wire `BankConfig` into the processors (or replace the per-processor hardcoded constants with `@ConfigurationProperties`-driven values) so success rate and latency are genuinely configurable per the original Phase 1 intent — this also make it trivial to dial failure rates up/down for chaos-testing the rest of the system.
- Add the missing idempotency guard on `PaymentInitiatedConsumer` using the already-available `idempotencyKey` (e.g. an in-memory/Redis dedup set with a short TTL, since this service is otherwise stateless).
- Add a `@RateLimiter` (Resilience4j, already a declared dependency) around the simulation call, both to fulfill the original roadmap goal and to make the "protect a downstream from overload" concept demonstrable in this project.
- Remove the dead `BankPaymentMethod` enum, `ACCOUNT_BLOCKED` value, and the unused `BankConfig` import — small cleanup, but reduces confusion for anyone reading this service cold.
- Consider making `simulateLatency` non-blocking (e.g. a scheduled delayed publish) if consumer throughput ever becomes a concern, especially for the NetBanking path.
- Add unit tests for each processor's approve/decline distribution (statistical assertion over N runs) and for `PaymentRouter`'s dispatch/`IllegalArgumentException` behavior — cheap, high-value tests given this service's logic is small and pure.
- Add a lightweight `NewTopic` `@Bean`/init step (or a shared provisioning script referenced from the root) so `payment.initiated`/`payment.processed` (and their retry/DLT topics) are created automatically in a fresh environment instead of requiring manual `kafka-topics --create` calls.

## 2026-07-04 — Stage 4 Infra & Config Refactor

### Issue

Ahead of Docker containerization and eventual AWS deployment, this service had several
config/infra gaps: a YAML indentation bug that silently unset `spring.application.name`,
a `BankConfig` class that was declared but never injected (so `bank.*` YAML values did
nothing and success-rate/latency were hardcoded `private static final` constants per
processor), no `SERVER_PORT`/`SPRING_PROFILES_ACTIVE` env-driven config, two dead `.env`
vars, a couple of small dead-code items flagged in this file, no `.env.example`, no
Spring profile split (dev/sit/prod), and no Dockerfile.

### Root Cause

Config wiring was never finished after the Phase 4 Strategy Pattern refactor moved
simulation logic into per-method processor classes (see "Configurable success rate"
item above) — the constants were hardcoded directly instead of reading from the
already-declared `BankConfig`. The YAML indentation bug was a plain typo. The
env/profile/Docker gaps are because this service was only ever run locally via
`bootRun` against dockerized infra, never itself containerized.

### Solution

- Fixed `application.yml`: `name: mock-bank-service` now correctly nested under
  `spring.application`.
- Restructured `BankConfig` (`config/BankConfig.java`) into three nested
  `Processor` groups (`card`, `upi`, `netBanking`), each with `successRate`,
  `minLatencyMs`, `maxLatencyMs`. Registered explicitly via
  `@EnableConfigurationProperties(BankConfig.class)` on `MockBankServiceApplication`
  (no existing repo convention for `@ConfigurationPropertiesScan` vs
  `@EnableConfigurationProperties` was found elsewhere in the codebase, so the latter
  was used per the task default; `BankConfig` also still carries its original
  `@Configuration` annotation, so it was already component-scanned as a bean — the
  `@EnableConfigurationProperties` addition is a belt-and-suspenders explicit
  registration, not a fix for a previously-missing bean).
- `CardProcessor`/`UpiProcessor`/`NetBankingProcessor` now take `BankConfig` via
  constructor injection (`@RequiredArgsConstructor`, matching this codebase's existing
  Lombok convention) and read `successRate`/`minLatencyMs`/`maxLatencyMs` from their
  respective nested group instead of hardcoded constants. Default YAML values
  (`0.85`/200-500ms, `0.92`/50-150ms, `0.78`/1000-3000ms) reproduce the exact prior
  hardcoded behavior — no behavior change.
- Removed the old flat `bank.success-rate`/`min-latency-ms`/`max-latency-ms` keys from
  `application.yml`, replaced with nested `bank.card.*`/`bank.upi.*`/`bank.net-banking.*`.
- Added `server.port: ${SERVER_PORT:8081}`; added `SERVER_PORT=8081` to `.env`.
- Added `SPRING_PROFILES_ACTIVE=dev` to `.env`.
- Removed dead `.env` vars `KAFKA_PAYMENT_INITIATED_TOPIC`/`KAFKA_PAYMENT_PROCESSED_TOPIC`
  (unread — topic names remain hardcoded string literals in
  `PaymentInitiatedConsumer`/`PaymentProcessedProducer`; making them config-driven is
  deferred as a slightly larger change touching `@KafkaListener`/producer call sites).
- Removed dead code: the unused `BankPaymentMethod` enum (duplicate of `PaymentMethod`),
  `FailureReason.ACCOUNT_BLOCKED` (never produced), and the now-still-unused
  `BankConfig` import in `BankPaymentServiceImpl` (that class never used `BankConfig`
  directly — the processors do).
- Created `.env.example` (none existed) listing `KAFKA_BOOTSTRAP_SERVERS`,
  `SERVER_PORT`, `SPRING_PROFILES_ACTIVE` — every var actually read after cleanup.
- Confirmed `spring-boot-starter-actuator` was already a `build.gradle` dependency (no
  change needed there); added `management.*`/`info.*` blocks to `application.yml`
  (health/info/metrics/prometheus exposure, always-show health details, graceful
  shutdown, 30s shutdown timeout).
- Added `application-dev.yml` (root DEBUG), `application-sit.yml` (root INFO),
  `application-prod.yml` (root WARN, health details `never`).
- Added a multi-stage `Dockerfile` (Temurin 21 Alpine build/runtime, non-root `spring`
  user, `/actuator/health` HEALTHCHECK) per the standard template used across services
  in this refactor pass.

### Files Modified

- `src/main/resources/application.yml` (indentation fix, nested `bank.*`, `SERVER_PORT`,
  `management`/`info` blocks, graceful shutdown)
- `src/main/resources/application-dev.yml` (new)
- `src/main/resources/application-sit.yml` (new)
- `src/main/resources/application-prod.yml` (new)
- `src/main/java/com/payments/mock_bank_service/config/BankConfig.java` (nested groups)
- `src/main/java/com/payments/mock_bank_service/MockBankServiceApplication.java`
  (`@EnableConfigurationProperties(BankConfig.class)`)
- `src/main/java/com/payments/mock_bank_service/processor/CardProcessor.java`
- `src/main/java/com/payments/mock_bank_service/processor/UpiProcessor.java`
- `src/main/java/com/payments/mock_bank_service/processor/NetBankingProcessor.java`
- `src/main/java/com/payments/mock_bank_service/service/impl/BankPaymentServiceImpl.java`
  (removed unused `BankConfig` import)
- `src/main/java/com/payments/mock_bank_service/type/FailureReason.java`
  (removed `ACCOUNT_BLOCKED`)
- `src/main/java/com/payments/mock_bank_service/type/BankPaymentMethod.java` (deleted)
- `.env` (added `SERVER_PORT`, `SPRING_PROFILES_ACTIVE`; removed dead topic vars)
- `.env.example` (new)
- `Dockerfile` (new)

### Impact
- Runtime: success-rate/latency per payment method are now genuinely configurable via
  YAML/env without a code change or recompile; default simulated behavior is unchanged.
- Deployment: service is now containerizable (multi-stage Dockerfile, non-root user,
  health-checked); port and active profile are externalized via env vars, ready for
  Docker/AWS environment injection.
- Performance: no change — `Thread.sleep`-based latency simulation is untouched per
  scope (explicitly deferred, see Follow-up).
- Security: runs as non-root `spring` user in the container image; no auth changes
  (this service has none, by design — see repo-root CLAUDE.md).
- Maintainability: removed two dead-code items and a dead import; `.env.example` now
  gives a single source of truth for which env vars this service actually reads.

### Follow-up

- Make Kafka topic names config-driven (`@Value`/`application.yml`-backed) instead of
  hardcoded string literals in `PaymentInitiatedConsumer`/`PaymentProcessedProducer` —
  deferred, larger change than this pass's scope.
- Idempotency guard on `PaymentInitiatedConsumer` (still open — out of scope here).
- `@RateLimiter`/other Resilience4j annotations (still open — out of scope here).
- Non-blocking latency simulation (still open — out of scope here, explicitly excluded
  by task instructions).
- `@DltHandler`/DLT monitoring (still open, system-wide gap — out of scope here).
