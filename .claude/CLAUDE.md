# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A learning/portfolio project simulating a real-world payment gateway (think Razorpay/Stripe) as a distributed system, built incrementally phase-by-phase (see `PROGRESS.md` for the full decision log — it is the authoritative source of *why* things were built the way they were, and documents several places where shipped behavior diverges from the original plan; prefer reading actual code over trusting its checklists as current-state truth). Business framing: the gateway doesn't care how a real bank processes a transaction internally, only what it sends/receives — hence `mock-bank-service` is a pure random-outcome simulator, not a real integration (see `DD_Distributed_Payments_Gateway.docx` for the original Phase-1 design reasoning).

Six independent Spring Boot services live under this repo, each with its own `.claude/CLAUDE.md` — **read the relevant service's file before working in it**, this root file only covers system-wide concerns:

| Service | Port | Own DB (host port) | Role |
|---|---|---|---|
| `payment-service` | 8080 | `payment_db` (5432) | Core: accepts payments, orchestrates async processing, merchant API-key gate |
| `mock-bank-service` | 8081 | none (stateless) | Simulates bank approval/decline per payment method |
| `merchant-service` | 8082 | `merchant_db` (5433) | Merchant registration + API key issuance/validation |
| `webhook-service` | 8083 | `webhook_db` (5434) | Delivers payment-status webhooks to merchants, with retry/backoff |
| `settlement-service` | 8084 | `settlement_db` (5435) | Nightly batch: aggregates successful payments per merchant |
| `gateway-service` | 9000 | none | Planned single entry point (Spring Cloud Gateway) — **early scaffolding only, see its own CLAUDE.md** |

There is **no root Gradle build** — each service directory is a fully independent Gradle project (own `build.gradle`, `settings.gradle`, `gradlew`). There is no shared library/module for common DTOs or enums: every service that needs e.g. `MerchantResponse`, `MerchantStatus`, `PaymentMethod`, `FailureReason`, or `TransactionStatus` maintains its **own independently duplicated copy**. If you change a contract in one service (e.g. merchant-service's response shape), you must manually update every consuming service's local copy — there is no compiler to catch drift across service boundaries.

## Commands

There is no top-level build command — work inside each service directory:
```bash
cd <service-name>
./gradlew build         # compile + test + package
./gradlew test          # run tests
./gradlew bootRun        # run locally (port per table above)
./gradlew test --tests "fully.qualified.TestClass"
```
On Windows, use `gradlew.bat` instead of `./gradlew` if not in a POSIX shell.

**Infra** (Postgres × 4, Redis, Zookeeper, Kafka) is provisioned by the root `docker-compose.yml`:
```bash
docker compose up -d        # start all infra containers
docker compose down          # stop (keeps volumes/data)
docker compose down -v       # stop AND delete all volumes — destroys all DB/Kafka/Redis data
```
See `DOCKER_COMMANDS.md` for a full cheat sheet (psql/redis-cli/kafka-console-producer usage, container names, etc.) — notably: **inside Docker, use the service name, not `localhost`**, to reach another container (e.g. `jdbc:postgresql://postgres:5432/...`, not `localhost`). Currently every service is run locally via `bootRun`/IDE against dockerized infra, not itself containerized — `docker-compose.yml` has no service definitions for the six Spring Boot apps themselves, only their infra dependencies.

`KAFKA_AUTO_CREATE_TOPICS_ENABLE` is set to `"false"` in `docker-compose.yml` — Kafka topics (see topic map below) must be created manually (`docker exec payment_kafka kafka-topics --create ...`, see `DOCKER_COMMANDS.md`) or auto-create must be temporarily enabled; there is no topic-provisioning script/init container anywhere in the repo.

Each service (except `gateway-service`) has its own `.env` (gitignored) with DB/Kafka connection values; only `payment-service` has a checked-in `.env.example` template, and it is itself out of date relative to what `application.yml` actually requires (see `payment-service/.claude/CLAUDE.md`). The other services have no `.env.example` at all — check each service's own CLAUDE.md for what variables it actually needs.

## System-wide event flow

The core async flow, spanning three services via Kafka (topic names are **hardcoded string literals** in every producer/consumer across the codebase — none of them are actually read from the `.env`/config vars that appear to define them, which are vestigial in every service that has them):

```
Client → POST /payments (payment-service, Idempotency-Key header, Bearer API key)
  → validates API key via merchant-service (sync, Feign, GET /merchants/validate-key)
  → saves Payment(PENDING) + OutboxEvent(PAYMENT_INITIATED) in one DB transaction
  → returns 202 Accepted immediately (Kafka publish is decoupled from the HTTP response)

payment-service: OutboxPublisherJob (every 5s, DB-lock guarded)
  → publishes topic "payment.initiated"

mock-bank-service: PaymentInitiatedConsumer (group mock-bank-group)
  → PaymentRouter dispatches by PaymentMethod to CardProcessor / UpiProcessor / NetBankingProcessor
    (Card 85%/200-500ms, UPI 92%/50-150ms, NetBanking 78%/1000-3000ms — hardcoded per-processor constants)
  → publishes topic "payment.processed" (APPROVED/DECLINED + failure reason)

Two independent consumer groups both read "payment.processed" (fan-out, not competing consumers):
  - payment-service: PaymentProcessedConsumer (group payment-group)
      → idempotent status update (only if still PENDING) → SUCCESS or FAILED
      → on SUCCESS, publishes topic "payment.succeeded" (no outbox durability on this leg — unlike
        payment.initiated, a producer failure here just logs and loses the event)
  - webhook-service: PaymentProcessedConsumer (group webhook-service)
      → looks up merchant's webhookUrl via merchant-service (sync, Feign, GET /merchants/{id})
      → writes a WebhookDelivery row (PENDING) — this row IS the outbox for this subsystem
      → WebhookRetryJob (every 10s) drains PENDING/FAILED rows due for retry, POSTs to the merchant's
        webhookUrl, exponential backoff 30s×2^(n-1), DEAD after 5 attempts

settlement-service: PaymentSucceededConsumer (group settlement-group)
  → mirrors "payment.succeeded" into its own local ledger table (settlement_payment_records) —
    settlement-service never reads payment-service's database directly
  → nightly @Scheduled(cron="0 0 0 * * *") tasklet aggregates unsettled records per merchant,
    writes a Settlement row, marks the local records settled=true
    (does NOT call back to payment-service — payment-service's own rows are never marked "SETTLED")
```

**Kafka topic map** (all keyed by `paymentId.toString()`, all JSON via Spring Kafka's Jackson serializer with `setAddTypeInfo(false)` — no type-id headers, consumers must know the target DTO shape out-of-band):

| Topic | Producer | Consumer(s) |
|---|---|---|
| `payment.initiated` | payment-service (via outbox) | mock-bank-service |
| `payment.processed` | mock-bank-service | payment-service AND webhook-service (independent groups) |
| `payment.succeeded` | payment-service | settlement-service |

Every Kafka consumer in the system uses the identical retry convention: `@RetryableTopic(attempts="3", backOff=@BackOff(delay=2000, multiplier=2.0), dltTopicSuffix=".DLT", topicSuffixingStrategy=SUFFIX_WITH_INDEX_VALUE, autoStartDltHandler="false")`. **No service anywhere defines a `@DltHandler`** — every `.DLT` topic across the system silently accumulates unconsumed messages; there is no dead-letter monitor endpoint anywhere despite `GET /admin/dlt-messages` being a long-standing planned item in `PROGRESS.md`. Note also that webhook-service's `@RetryableTopic` is effectively dead code in practice because its listener catches all exceptions internally and never rethrows — the Kafka-level retry mechanism never actually triggers there (only the DB-level `WebhookRetryJob` provides real retry for that service).

## Structural conventions shared across services

- **Java 21, Spring Boot 4.0.6, Spring Cloud 2025.1.1** everywhere. Spring Boot 4 renames some starters (`spring-boot-starter-webmvc` instead of `-web` in newer services; `spring-boot-starter-flyway` as a dedicated starter). Every service uses Jackson 3 (`tools.jackson.databind.*`) for its own `ObjectMapper`/Kafka serializer usage; only `payment-service`'s `PaymentResponse` DTO additionally imports one classic Jackson 2 annotation (`com.fasterxml.jackson.annotation.JsonPropertyOrder`) alongside Jackson 3 — that's the one place in the whole repo where the two package families are mixed, and worth double-checking before editing that file.
- **Flyway owns every schema**; `ddl-auto` is `validate` or `none` everywhere — never `update`/`create`. Each service has its own Postgres instance/database; there is no shared database or cross-service foreign keys. Any "join" across service data happens either synchronously via a Feign HTTP call (payment-service/webhook-service → merchant-service) or asynchronously by a service mirroring another's events into its own local table (settlement-service mirrors payment-service's successes via Kafka rather than reading its DB).
- **Transactional outbox pattern** exists in exactly one place done "properly": payment-service's `OutboxEvent`/`OutboxPublisherJob` for `payment.initiated`, guarded by a hand-rolled Postgres-row-based distributed lock (`common.lock.LockService`/`DistributedLock` — not ShedLock, not Redisson, despite Redis being available in the stack). webhook-service's `webhook_deliveries` table plays a similar "outbox" role for its own delivery attempts but has no distributed-lock protection against multiple instances. Neither `payment.succeeded` (payment-service) nor `payment.processed` (mock-bank-service) publishing goes through an outbox — both are fire-and-forget Kafka sends behind only a circuit breaker.
- **Resilience4j is inconsistently wired**: every service that depends on it declares circuit-breaker/retry/timelimiter dependencies, but in practice only `@CircuitBreaker` is ever actually annotated onto code (payment-service's Kafka producers, webhook-service's HTTP delivery call) — `@Retry`/`@TimeLimiter` are frequently configured in YAML (webhook-service) or fully declared-but-unused as dependencies (mock-bank-service) without a matching annotation anywhere. Don't assume a `resilience4j.retry.instances.X` YAML block means retries are actually happening — check for the annotation.
- **API-key auth, not JWT**, is the real cross-service auth mechanism (merchant-service issues/validates SHA-256-hashed keys prefixed `sk_live`). Several services (payment-service, merchant-service) carry unused `jwt.*`/`auth.*` config left over from an early Phase-1 design — this is dead scaffolding everywhere it appears, not active security. See each service's CLAUDE.md for exactly how (or whether) it enforces this.
- **Inter-service HTTP calls use hardcoded `http://localhost:<port>` URLs** (via OpenFeign) — no service discovery/registry, no config-driven base URLs. This will need to change before any non-localhost/containerized deployment of the services themselves.
- **Logging**: every service writes to its own `logs/<service-name>.log` with daily rolling policy (gitignored). Package-level log levels are commonly dialed down for noisy scheduled pollers (e.g. outbox/webhook job packages set to `WARN`).
- **Testing is essentially absent repo-wide**: every service has exactly one test class (a `@SpringBootTest` context-load smoke test), and several of those are `@Disabled`. Do not assume any existing behavior is regression-tested — verify manually when changing business logic. The README references Bruno for API testing and `PROGRESS.md` lists "Bruno request collection committed" as an open Phase 7 item — no Bruno collection exists in the repo yet, so there's no committed request suite to run against.
- **Swagger/OpenAPI is only wired up in two of the six services**: `payment-service` and `mock-bank-service` depend on `springdoc-openapi-starter-webmvc-ui` and annotate their controllers/DTOs for it (UI at the default `/swagger-ui.html`, JSON at `/v3/api-docs`). `merchant-service`, `webhook-service`, `settlement-service`, and `gateway-service` have no OpenAPI dependency or annotations at all — don't expect a Swagger UI on those.

## Known cross-cutting gaps (verified in code, not just PROGRESS.md wishlist items)

- Dead-letter topics (`*.DLT`) have no consumers/monitoring anywhere in the system.
- `.env` files across most services define several variables (topic names, retry tuning, alternate service URLs) that are **not actually read by any code** — the real values are hardcoded Java literals. Don't assume editing a `.env` value changes runtime behavior without checking the corresponding `application.yml`/`@Value` usage first.
- No shared contract/library between services — enum and DTO drift across service boundaries is a real risk when changing any cross-service-visible type (`MerchantResponse`, `MerchantStatus`, `PaymentMethod`, `FailureReason`, `TransactionStatus`, `CurrencyCode` are all independently redefined per service).
- `gateway-service` (Phase 7, port 9000) is real but minimal: a handful of static `RouterFunction` routes to hardcoded `localhost` ports for payment/merchant/webhook/settlement services, no tracing, no Prometheus, no rate limiting, no Swagger aggregation yet — see its own CLAUDE.md before assuming any Phase 7 observability item is done.
- Settlement never reports back to payment-service (payments are never marked "SETTLED" in `payment-service`'s own table) — the two services' views of settlement status are ships passing in the night.

## Where to look for more

- `PROGRESS.md` — phase-by-phase build log with explicit design decisions and reasoning (valuable for *why*, but cross-check against code for *what actually shipped* — several documented plans, like settlement's chunk size or its 11:59 PM trigger time, diverge from the shipped implementation; see `settlement-service/.claude/CLAUDE.md`).
- `README.md` — service/port table and tech stack at a glance.
- `DOCKER_COMMANDS.md` — infra cheat sheet (Postgres/Redis/Kafka container commands).
- `DD_Distributed_Payments_Gateway.docx` — original Phase-1 design-decision narrative (only covers Phase 1 in depth; superseded/extended by `PROGRESS.md` for later phases).
- Each service's own `.claude/CLAUDE.md` — package layout, endpoints, business logic, and known gaps specific to that service.
