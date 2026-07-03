# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Service overview

`settlement-service` performs end-of-day batch settlement of successful payments, grouping them by merchant. Port **8084**, own Postgres (`settlement_db`, host port 5435). It does **not** connect to payment-service's database — it maintains its own local replica of "successful payments" via a Kafka-consumed ledger table, then batches over that replica.

See the repo-root `.claude/CLAUDE.md` for the system-wide event flow and other services.

## Commands

```bash
./gradlew build
./gradlew test
./gradlew test --tests "com.payments.settlement_service.SettlementServiceApplicationTests"
./gradlew bootRun   # needs settlement-db (host port 5435) and Kafka reachable
```

Base package: `com.payments.settlement_service`.

## Architecture

```
config/    SettlementBatchConfig (Job/Step wiring), KafkaConsumerConfig
domain/    Settlement (entity), SettlementPaymentRecord (entity, the local payment ledger),
           SettlementCreationRequest (record DTO), SettlementProcessor (ItemProcessor),
           SettlementScheduler (@Scheduled cron trigger), SettlementStatus (enum),
           SettlementTasklet (the actual step logic)
event/     PaymentSucceededConsumer, PaymentSucceededEvent
repository/ MerchantSettlementAggregate (JPQL projection record),
           SettlementPaymentRecordRepository, SettlementRepository
service/   SettlementAggregationService (+impl), SettlementWriterService
type/      CurrencyCode
```

**No REST controller exists anywhere in this service** — confirmed absence. There is no `GET /settlements?merchantId=X&date=Y` and no manual trigger endpoint, even though `spring-boot-starter-webmvc` is a declared dependency. Root `PROGRESS.md`'s checklist explicitly lists both as unchecked TODOs.

## How payments get into this service (no direct DB/API coupling to payment-service)

`payment-service` publishes a `PaymentSucceededEvent` to Kafka topic **`payment.succeeded`** after a payment transitions to `SUCCESS` (see payment-service's CLAUDE.md — `PaymentSucceededProducer`, wrapped in a Resilience4j circuit breaker with **no outbox durability**, unlike the `payment.initiated` path). `PaymentSucceededConsumer` here (group `settlement-group`) consumes it, checks `repository.existsByPaymentId(...)` for idempotency (duplicate events are logged and ignored), and persists a `SettlementPaymentRecord` row (`settled=false`) in `settlement_db`. **This locally-mirrored table — not payment-service's DB — is what the nightly batch job reads.** Consumer uses the same `@RetryableTopic(attempts="3", backOff 2s×2.0, dltTopicSuffix=".DLT", autoStartDltHandler="false")` pattern used across the other services; no `@DltHandler` exists, so `payment.succeeded.DLT` messages are unconsumed today.

## Batch job — important: this is NOT chunk-oriented despite what PROGRESS.md says

Root `PROGRESS.md`'s Phase 6 planning notes claim "Chunk size 100 implemented," but the **actual shipped implementation is Tasklet-based**, not chunk-oriented (reader/processor/writer with a chunk size). Trust the code over the docs here:

```java
// SettlementBatchConfig
settlementStep = new StepBuilder("settlementStep", jobRepository)
        .tasklet(settlementTasklet, transactionManager).build();
settlementJob  = new JobBuilder("settlementJob", jobRepository)
        .start(settlementStep).build();
```
- A `ListItemReader<MerchantSettlementAggregate> settlementReader()` bean **is defined but never wired into any Step** — dead code left over from an earlier chunk-oriented design. Don't assume chunking/paging happens anywhere in the batch path.
- `SettlementTasklet.execute()` loads **all** unsettled merchant aggregates into memory in one shot (`settlementAggregationService.getAggregates()`), then loops in plain Java calling `settlementProcessor.process(aggregate)` → `settlementWriterService.createSettlement(request)` for every merchant, all inside a single tasklet transaction.
- The "reader" query is one JPQL aggregate: `SELECT new MerchantSettlementAggregate(r.merchantId, SUM(r.amount), COUNT(r)) FROM SettlementPaymentRecord r WHERE r.settled = false GROUP BY r.merchantId` — one row per merchant, not per-payment.
- No `.faultTolerant()`, `.skip()`, `.retry()`, no `JobExecutionListener`/`StepExecutionListener` — no fault tolerance at the Batch-framework level at all.

## Scheduling

`SettlementScheduler`: `@Scheduled(cron = "0 0 0 * * *")` — **this fires at midnight (00:00:00), not 11:59 PM** as `PROGRESS.md`'s planning notes originally intended. Document/trust the shipped cron, not the docs, if asked about run time.

- Launches via `jobOperator.start(settlementJob, params)` (the newer `JobOperator` API, not `JobLauncher`).
- Job parameters: only `timestamp` (`System.currentTimeMillis()`) — this exists purely so Spring Batch's `JobRepository` doesn't reject a rerun as a duplicate `JobInstance`; it deliberately allows reruns rather than using a `RunIdIncrementer`.
- **No manual/admin trigger endpoint** — confirmed absent, matches `PROGRESS.md`'s unchecked TODO.
- `spring.batch.job.enabled: false` — prevents Spring Boot Batch from auto-running `settlementJob` on every app startup; the cron is the sole trigger today.

## Idempotency — has a real gap, not fully safe on reruns

1. **Ingestion side** (safe): `PaymentSucceededConsumer` checks `existsByPaymentId` before inserting.
2. **Settlement-write side** (mostly safe, but not bulletproof): protected by a DB unique constraint `uk_settlement_merchant_period (merchant_id, period_date)`. A pure rerun on the same day with zero new unsettled records finds nothing to do (query filters `settled=false`) — naturally idempotent.
   **However**, if new unsettled payments arrive for a merchant *after* that merchant already got a settlement today, and the job runs again the same day, `SettlementWriterService.createSettlement()` attempts a second `INSERT` for the same `(merchant_id, today)` pair and throws `DataIntegrityViolationException` — **this is not caught anywhere**, so it fails that tasklet/step (Spring Batch marks the execution `FAILED`). Each merchant's settlement write is its own `@Transactional` call, so earlier merchants in that run's loop keep their commits, but the merchant that collided (and any after it in iteration order) are left unprocessed. Flag this explicitly if asked to harden reruns.

## Marking payments SETTLED — entirely local, no callback to payment-service

`SettlementWriterService.createSettlement()` (one `@Transactional` method per merchant):
1. Saves a new `Settlement` row with `status` hardcoded to `COMPLETED` (`PENDING`/`PROCESSING`/`FAILED` enum values are defined but never actually assigned anywhere).
2. Re-queries `findByMerchantIdAndSettledFalse(merchantId)` and sets `settled=true` + `settlementId` on each matching `SettlementPaymentRecord`.

This only updates settlement-service's own `settlement_payment_records` table — **there is no REST call or Kafka event back to payment-service**. Payment-service's own `payments` table/status is untouched by this service; if payment-service needs to know a payment was settled, that integration doesn't exist in the current codebase.

## Data model (Flyway `V1__create_settlement_tables.sql`)

```
settlements                 id, merchant_id, total_amount NUMERIC(19,4), payment_count,
                             period_date DATE, status, created_at
                             UNIQUE (merchant_id, period_date)  -- idempotency guard, see above
settlement_payment_records  payment_id (PK, same UUID as payment-service's payment id), merchant_id,
                             amount NUMERIC(19,4), currency, payment_created_at, settled BOOLEAN,
                             settlement_id (nullable), created_at
                             indexes: idx_spr_merchant, idx_spr_settled, idx_spr_merchant_settled
```
`spring.jpa.hibernate.ddl-auto: none` — Hibernate does not even validate against Flyway's schema here (stricter than most sibling services, which use `validate`).

## Configuration (`application.yml`, single profile)

```yaml
server.port: 8084   # hardcoded, not env-driven (unlike other services' ${X_PORT:default} pattern)
spring.datasource: url/username/password from DB_URL/DB_USERNAME/DB_PASSWORD
spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.consumer.auto-offset-reset: earliest
spring.batch.jdbc.initialize-schema: always   # Spring Batch's own metadata tables auto-created every startup
spring.batch.job.enabled: false
```
`.env` defines `DB_URL=jdbc:postgresql://localhost:5435/settlement_db`, `DB_USERNAME=settlement_user`, `DB_PASSWORD=settlement_pass`, `KAFKA_BOOTSTRAP_SERVERS`, plus unused leftovers (`REDIS_HOST`/`PORT`, `KAFKA_PAYMENT_INITIATED_TOPIC`, `KAFKA_PAYMENT_PROCESSED_TOPIC` — none of these are referenced by any Java code; the topic actually consumed, `payment.succeeded`, isn't even named in `.env`). No `.env.example` is checked in for this service.

## Known gaps (don't assume these work)

- No REST API surface at all (no query endpoint, no manual trigger).
- Chunk-oriented batch design described in `PROGRESS.md` was never actually implemented — it's a single Tasklet with an unused dead `ListItemReader` bean.
- Settlement rerun on the same day with newly-arrived unsettled payments can throw an uncaught `DataIntegrityViolationException` and fail the job.
- No distributed lock guarding concurrent job execution across multiple instances (contrast with payment-service's `LockService`/`DistributedLock`) — Spring Batch's own `JobInstance` uniqueness is the only safeguard, and it's deliberately weakened by the always-unique `timestamp` job parameter.
- Only one test exists (`SettlementServiceApplicationTests`, empty `contextLoads()`) — no coverage of the tasklet, processor, writer, or consumer.
- No Resilience4j/OpenFeign in this service at all — it never calls another service synchronously.
