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

## Additional Improvements (suggested future roadmap)

- Decide and implement the actual chunk-oriented Spring Batch job the roadmap describes (`.chunk(100, transactionManager).reader(...).processor(...).writer(...)`), or explicitly rewrite `PROGRESS.md`'s claim and delete the dead `ListItemReader` bean if the Tasklet approach is the deliberate final design.
- Catch `DataIntegrityViolationException` in `SettlementWriterService.createSettlement()` for the same-day-rerun-with-new-payments case — either merge the new unsettled records into the existing settlement for that `(merchant, date)` pair, or skip-and-log that merchant for this run rather than failing the whole job execution.
- Add a distributed lock (mirroring payment-service's `LockService`/`DistributedLock`) around `SettlementScheduler`'s job launch before this service is ever run with more than one instance.
- Build the two still-missing endpoints: `GET /settlements?merchantId=X&date=Y` and a manual admin trigger — both are small, well-scoped, and unblock manual testing/ops recovery today.
- Decide how (or whether) `payment-service` should learn that a payment has been settled — options include adding a `SETTLED` value to `PaymentStatus` and having settlement-service call back (Kafka event or Feign), or deliberately keeping settlement status separate and exposing it only via this service's own query endpoint. Either way, document the decision.
- Actually assign `SettlementStatus.FAILED`/`PROCESSING` where appropriate instead of hardcoding `COMPLETED`, so a partially-failed run is visible in the data rather than silently absent.
- Add Testcontainers-based tests for `SettlementTasklet`/`SettlementWriterService`/`PaymentSucceededConsumer`, particularly around the idempotency edge case identified above.
- Once the query endpoint exists, add springdoc-openapi annotations to close the Swagger gap relative to payment-service/mock-bank-service.
