# PENDING.md — settlement-service

Tracks outstanding work for this service, consolidated from `PROGRESS.md`, the original roadmap PDF (`distributed_payment_gateway_roadmap(11).pdf`), and direct code analysis. See `.claude/CLAUDE.md` (this service and root) for architecture context behind each item.

## From PROGRESS.md

- **`GET /settlements?merchantId=X&date=Y`** (Phase 6 checklist, unchecked) — no REST controller exists in this service at all; there is no way to query settlement results via API.
- **Manual trigger endpoint** (Phase 6 checklist, unchecked — roadmap specifies `POST /admin/settlements/trigger`) — the only way to run the job is the nightly cron; there's no way to force a run for testing or ops recovery.
- **Outbox pattern for the `payment.succeeded` flow** (Phase 6 checklist, unchecked) — this is really a payment-service-side fix (see its own `PENDING.md`), but it directly affects this service's data completeness: if `payment-service` ever fails to publish `payment.succeeded` (no outbox durability on that leg today), a successful payment will simply never appear in this service's `settlement_payment_records` ledger, with no error surfaced anywhere.
- **⚠️ Claimed done but not fully verified in code — "Chunk size 100 implemented"** (Phase 6 checklist, checked ✅) — the actual implementation is **Tasklet-based, not chunk-oriented**. A `ListItemReader<MerchantSettlementAggregate>` bean is defined but never wired into any `Step`; `SettlementTasklet` loads *all* unsettled merchant aggregates into memory in one shot inside a single transaction, with no chunking/paging at all. Treat this checklist item as not actually delivered as originally scoped.
- **⚠️ Claimed done but has an edge case — "Idempotent rerun behavior verified"** (Phase 6 checklist, checked ✅) — true for the common case (a same-day rerun with zero new unsettled payments is a no-op), but if new unsettled payments arrive for a merchant *after* that merchant already has a settlement for today, a rerun throws an uncaught `DataIntegrityViolationException` on the `uk_settlement_merchant_period` unique constraint and fails the whole job, leaving any merchants later in iteration order unprocessed that day.

## From the roadmap PDF

- **Spring Batch reader/processor/writer with chunk size 100** ("ItemReader — reads SUCCESS payments from DB in chunks of 100") — this is the same gap as above, called out explicitly by the original design spec: the roadmap wanted real chunked processing, not a single in-memory tasklet loop.
- **`@Scheduled` trigger at 11:59 PM** — the shipped cron is `0 0 0 * * *`, i.e. **midnight**, not 11:59 PM as both the roadmap and `PROGRESS.md`'s own planning notes specify. Functionally close enough for a daily batch, but worth fixing or consciously re-documenting.
- **Phase 7 — Swagger, tracing, Prometheus** — none present; this service has no REST controller at all yet, so Swagger has nothing to document until the query/trigger endpoints above are built.

## Holistic gaps (missed by both PROGRESS.md and the roadmap)

- **Settlement never feeds back into `payment-service`.** `payment-service`'s `PaymentStatus` enum is only `PENDING`/`SUCCESS`/`FAILED` — there is **no `SETTLED` value at all**. "Payments marked SETTLED after batch" (checked ✅ in `PROGRESS.md`'s Phase 6 checklist) is only true within this service's own local mirror table (`settlement_payment_records.settled`) — `payment-service`'s own `payments` rows are never touched and have no concept of "settled" in their own schema. Anyone reading the checklist as "payment-service reflects settlement status" would be wrong; this is a meaningful gap between the checklist's wording and what actually happens, worth a deliberate design decision (add a `SETTLED` status? a separate settlement-status lookup? leave it as-is and document why?).
- **No distributed lock around the scheduled job** — unlike payment-service's `OutboxPublisherJob` (which uses `LockService`), nothing here prevents two instances of this service (if ever scaled beyond one) from both picking up and processing the same unsettled aggregates concurrently.
- **`Settlement.status` is always hardcoded to `COMPLETED`** — the `SettlementStatus` enum also defines `PENDING`/`PROCESSING`/`FAILED`, but nothing in the code path ever assigns them; a settlement that fails partway (see the `DataIntegrityViolationException` gap above) doesn't get recorded as `FAILED`, it just isn't recorded at all for the merchants after the failure point.
- **No Resilience4j/OpenFeign in this service** — it never calls another service synchronously, which is fine architecturally, but also means there's no resilience pattern practiced here at all (in contrast to the rest of the system) — worth at least a note in any future resilience audit.
- **Only one test exists** (`SettlementServiceApplicationTests`, empty `contextLoads()`) — zero coverage of the tasklet, the aggregation query, the writer's idempotency behavior, or the Kafka consumer.

## 2026-07-04 — Stage 4 Infra & Config Refactor

### Issue

settlement-service's `.env`/`application.yml` used inconsistent naming (`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` instead of the `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` convention adopted repo-wide), a hardcoded `server.port: 8084` with no env override, `spring.jpa.hibernate.ddl-auto: none` (the only service that didn't even validate against its Flyway schema), no Spring profiles (dev/sit/prod), no `.env.example`, several dead `.env` vars (`REDIS_HOST`, `REDIS_PORT`, `KAFKA_PAYMENT_INITIATED_TOPIC`, `KAFKA_PAYMENT_PROCESSED_TOPIC`), and no Dockerfile — all blockers for containerizing this service and deploying it to AWS alongside its siblings.

### Root Cause

This service was scaffolded early (Phase 6) before the cross-service config conventions (env-var naming, actuator, profiles, graceful shutdown, Dockerfiles) were standardized in later phases. It also never had a Redis/Feign/Resilience4j dependency, so the `.env`'s `REDIS_*` entries were copy-pasted boilerplate that was never wired to any code, and the two `KAFKA_*_TOPIC` vars were leftover from an earlier design — the service actually consumes `payment.succeeded`, hardcoded as a string literal in `PaymentSucceededConsumer`, never read from any env var.

### Solution

- Renamed DB env vars to `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`; `application.yml`'s datasource URL is now composed as `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`.
- Made `server.port` env-driven via `${SERVER_PORT:8084}`; added `server.shutdown: graceful` and `spring.lifecycle.timeout-per-shutdown-phase: 30s`.
- Added `SPRING_PROFILES_ACTIVE=dev` to `.env`, plus new `application-dev.yml` (DEBUG root logging), `application-sit.yml` (INFO), `application-prod.yml` (WARN root logging, `management.endpoint.health.show-details: never`).
- Deleted dead `.env` vars: `REDIS_HOST`, `REDIS_PORT`, `KAFKA_PAYMENT_INITIATED_TOPIC`, `KAFKA_PAYMENT_PROCESSED_TOPIC` — confirmed via grep that no Java code in this service references any of them (no Redis client/config anywhere; `payment.succeeded` is a string literal in `PaymentSucceededConsumer`, not sourced from config).
- **Changed `spring.jpa.hibernate.ddl-auto` from `none` to `validate` and verified it — see below. Adopted successfully, no fallback needed.**
- `spring-boot-starter-actuator` was already present in `build.gradle` (no change needed there — the task brief's assumption that it was missing didn't hold for this service). Added `management.endpoints.web.exposure.include: health, info, metrics, prometheus`, `management.endpoint.health.show-details: always`, `management.info.env.enabled: true`, and an `info.app.name`/`info.app.description` block to `application.yml`.
- Created `.env.example` mirroring the cleaned-up `.env` (no secrets, same placeholder-style values already used for local dev).
- Created a multi-stage `Dockerfile` (Gradle build stage on `eclipse-temurin:21-jdk-alpine`, runtime on `eclipse-temurin:21-jre-alpine`, non-root `spring` user, `/actuator/health`-based `HEALTHCHECK`, `EXPOSE 8084`).

### `ddl-auto` verification (validate — adopted, not reverted)

Attempted `validate` first per instructions. To verify for real (not just by inspection), I started the `settlement-db` Postgres container (`docker compose up -d settlement-db` from repo root, infra-only, no compose file edits) and ran `./gradlew test --tests SettlementServiceApplicationTests` with the new env vars exported. Result: **BUILD SUCCESSFUL** — Flyway applied `V1__create_settlement_tables.sql` cleanly (confirmed via `flyway_schema_history`, `success=t`), and the Spring context (which triggers Hibernate's `SchemaValidator` at startup under `ddl-auto: validate`) loaded without error. Manually cross-checked `Settlement`/`SettlementPaymentRecord` entities against the migration DDL — every `@Column` name/nullability lines up with the table definition (`settlements`, `settlement_payment_records`), so this wasn't a fluke. Kafka connection-refused warnings appeared in the log (no local broker running) but those are async listener retries and did not affect context startup or the test outcome. `ddl-auto: validate` is kept as the final value; `none` is not needed as a fallback.

### Files Modified

- `settlement-service/.env` — replaced with standardized vars (`DB_HOST/PORT/NAME/USER/PASSWORD`, `SERVER_PORT`, `SPRING_PROFILES_ACTIVE`), dead Redis/Kafka-topic vars removed.
- `settlement-service/.env.example` — created (new).
- `settlement-service/src/main/resources/application.yml` — datasource URL composition, `ddl-auto: validate`, env-driven `server.port`, graceful shutdown, actuator/info blocks.
- `settlement-service/src/main/resources/application-dev.yml` — created.
- `settlement-service/src/main/resources/application-sit.yml` — created.
- `settlement-service/src/main/resources/application-prod.yml` — created.
- `settlement-service/Dockerfile` — created.
- `settlement-service/.claude/CLAUDE.md` — Configuration section updated to reflect the new env vars, `ddl-auto: validate`, and env-driven port.
- `settlement-service/.claude/PENDING.md` — this entry.

### Impact
- **Runtime**: No behavior change to the batch job, consumer, or writer logic — purely config/infra. `ddl-auto: validate` means Hibernate now fails fast on any future entity/schema drift instead of silently ignoring it (this service was previously the only one in the system without that safety net).
- **Deployment**: Service is now containerizable (Dockerfile added) and its port/DB connection are fully env-driven, matching every other service ahead of AWS deployment.
- **Performance**: Negligible — `validate` runs one metadata check at startup, no different in cost from `none`.
- **Security**: No secrets added to `.env.example`; actuator's `health`/`info`/`metrics`/`prometheus` endpoints are exposed with `show-details: always` in dev/sit but `never` in prod, consistent with not leaking internals in a production-like profile.
- **Maintainability**: Removing 4 dead `.env` vars and standardizing names cuts confusion for anyone cross-referencing this service against its siblings; profile files make per-environment log verbosity explicit instead of implicit.

### Follow-up
- None new from this pass. Pre-existing known gaps (tasklet-vs-chunk design, same-day-rerun `DataIntegrityViolationException`, missing REST endpoints, no distributed lock on the scheduler) are unchanged and out of scope here — see the sections above and `PROGRESS.md`.

## Additional Improvements (suggested future roadmap)

- Decide and implement the actual chunk-oriented Spring Batch job the roadmap describes (`.chunk(100, transactionManager).reader(...).processor(...).writer(...)`), or explicitly rewrite `PROGRESS.md`'s claim and delete the dead `ListItemReader` bean if the Tasklet approach is the deliberate final design.
- Catch `DataIntegrityViolationException` in `SettlementWriterService.createSettlement()` for the same-day-rerun-with-new-payments case — either merge the new unsettled records into the existing settlement for that `(merchant, date)` pair, or skip-and-log that merchant for this run rather than failing the whole job execution.
- Add a distributed lock (mirroring payment-service's `LockService`/`DistributedLock`) around `SettlementScheduler`'s job launch before this service is ever run with more than one instance.
- Build the two still-missing endpoints: `GET /settlements?merchantId=X&date=Y` and a manual admin trigger — both are small, well-scoped, and unblock manual testing/ops recovery today.
- Decide how (or whether) `payment-service` should learn that a payment has been settled — options include adding a `SETTLED` value to `PaymentStatus` and having settlement-service call back (Kafka event or Feign), or deliberately keeping settlement status separate and exposing it only via this service's own query endpoint. Either way, document the decision.
- Actually assign `SettlementStatus.FAILED`/`PROCESSING` where appropriate instead of hardcoding `COMPLETED`, so a partially-failed run is visible in the data rather than silently absent.
- Add Testcontainers-based tests for `SettlementTasklet`/`SettlementWriterService`/`PaymentSucceededConsumer`, particularly around the idempotency edge case identified above.
- Once the query endpoint exists, add springdoc-openapi annotations to close the Swagger gap relative to payment-service/mock-bank-service.
