# Build Progress

## Phase 1 — Core Foundation
**Status:** 🟢 Completed
**Branch:** `phase/1-core-foundation`

### Done
- Project structure and initial Gradle setup
- Docker Compose for Postgres + Redis
- Flyway schema management
- Payment entity, repository, DTOs
- Global exception handling
- `POST /api/v1/payments` with idempotency
- `GET /api/v1/payments/{id}`
- Redis cache with DB fallback
- Currency validation
- JWT auth
- Mock Bank service
- RestClient with timeout
- End-to-end flow validated in Bruno
- Logging to dedicated file
- `.env` support

### Decisions
| Decision | Reason |
|---|---|
| UUID primary key | Avoid volume leaks, safe for distributed ids |
| DECIMAL(19,4) | Exact money precision, no float |
| Idempotency key in header | Separate request identity from payload |
| Redis as cache with DB fallback | Redis failure must not break payment reads |
| DataIntegrityViolationException guard | Race protection for concurrent same-idempotency requests |
| Flyway owns schema | Single source of schema truth |
| Auth in payment service for now | Keep scope smaller; split later in Phase 3 |

---

## Phase 2 — Kafka Async Processing
**Status:** 🟢 Completed
**Branch:** `phase/2-Async-processing`

### Current tasks
- Define `payment.initiated` and `payment.processed` topics
- Move Mock Bank integration from HTTP to Kafka consumer/producer
- Add Payment Service consumer for processed events
- Change `POST /payments` to return 202 Accepted and treat processing as async
- Keep `GET /payments/{id}` for state polling
- Add duplicate-safe consumer logic using idempotency key

### Flow
`Client → POST /payments → Payment Service saves PENDING → publishes payment.initiated → returns 202 → Mock Bank consumes → publishes payment.processed → Payment Service consumes → updates status SUCCESS/FAILED`

### Notes
- Kafka is at-least-once, so duplicate events are expected
- Consumer must be idempotent on payment status updates
- Avoid duplicate payments or double status transitions

### Checklist
- ✅ Kafka available locally (Docker/local)
- ✅ `payment.initiated` topic created
- ✅ `payment.processed` topic created
- ✅ Mock Bank is Kafka consumer
- ✅ Mock Bank publishes `payment.processed`
- ✅ Payment Service consumes processed events
- ✅ `POST /payments` returns 202 Accepted
- ✅ `GET /payments/{id}` reports current status
- ✅ Idempotent consumer logic implemented
- ✅ Consumer crash/restart recovery verified
- ✅ Code cleanup
- ✅ Async flow tested in Bruno

### Decisions
| Decision | Reason |
|---|---|
| Kafka instead of sync HTTP | Decouple services, avoid blocking downstream failures |
| At-least-once + idempotency | Accept retries and duplicates, make handlers safe |
| Two-topic separation | Keep initiated and processed paths separate |
| 202 Accepted | Client gets immediate acknowledgement, not final result |
| Consumer idempotency | Reuse Phase 1 idempotency guard for event processing |

---

## Phase 3 — Merchant Side & Webhooks
**Status:** 🟢 Completed 
**Branch:** `phase/3-merchant-webhooks`

### Planned work (draft)
- Add merchant model and API key registration
- Implement API key auth on payment endpoints
- Store merchant webhook callback URL
- Add webhook service and retry worker
- Implement exponential backoff and DEAD state
- Add outbox pattern for webhook events

### Webhook flow
`Payment status changes → write webhook event to outbox → separate publisher publishes → worker POSTs to merchant callback → 2xx marks delivered → non-2xx/timeouts retry → max 5 failures mark DEAD`

### Planning notes
- Keep DB update + Kafka publish atomic where possible
- Outbox is the planned solution for partial failure between payment update and webhook publish
- Webhook retries should avoid hammering broken callbacks

### Planned checklist
- ✅ Merchant Service scaffolded
- ✅ `POST /merchants/register` returns API key
- ✅ API key validation added to payment requests
- ✅ Merchant webhook URL persisted
- ✅ Webhook Service created
- ✅ Webhook delivery implemented
- ✅ Retry with exponential backoff added
- ✅ Outbox pattern implemented
- ✅ Dead webhook handling added
- ✅ Delivery tested with webhook.site/local echo

# Phase 3 - Cleanup & Polish Tasks

## Payment Service
- ✅ Distributed locking for OutboxPublisherJob (prevent concurrent executions)
- [ ] Idempotency on Outbox publishing (prevent duplicate Kafka publishes)

## Webhook Service
- [ ] Webhook signature verification (for security - verify requests came from gateway)
- [ ] Webhook delivery metrics/monitoring (track success rates, latency)

## Merchant Service
- [ ] Multiple API keys per merchant support
- [ ] Merchant status management (ACTIVE/INACTIVE/SUSPENDED enforcement)

## General
- [ ] Error handling improvements (graceful degradation, better error messages)
- [ ] API documentation (Swagger/OpenAPI on all endpoints)

### Decisions
| Decision | Reason |
|---|---|
| Merchant Service separate | Keep payments focused, isolate merchant concerns |
| API key auth | Simpler rotation than JWT for merchants |
| Outbox for webhooks | Prevent lost webhook events on partial commit |
| Backoff retries | Protect merchant endpoints from repeated failures |
| Dead letter tracking | Need a place to inspect permanently failed deliveries |

---

## Phase 4 — Multiple Payment Methods
**Status:** 🟢 Completed
**Branch:** `phase/4-payment-methods`

### Planned work (draft)
- Add `paymentMethod` to payment request model
- Implement `PaymentProcessor` abstraction
- Add Card, UPI, NetBanking processors with distinct latency/failure behavior
- Add router to select processor by method
- Return method-specific failure codes

### Behaviour matrix
| Method | Latency | Success | Failure modes |
|---|---|---|---|
| Card | 200–500ms | 85% | Insufficient funds, expired card, CVV mismatch |
| UPI | 50–150ms | 92% | UPI ID not found, daily limit exceeded |
| NetBanking | 1–3s | 78% | Bank timeout, session expired |

### Planning notes
- Goal is extensible processor wiring, not hard-coded switch logic
- Strategy-style router should allow adding methods with minimal changes

### Planned checklist
- ✅ `PaymentProcessor` interface defined
- ✅ CardProcessor implemented
- ✅ UpiProcessor implemented
- ✅ NetBankingProcessor implemented
- ✅ `PaymentRouter` wired by method
- ✅ `paymentMethod` enum added to request
- ✅ Failure codes are specific per method
- ✅ All methods validated in Bruno

### Decisions
| Decision | Reason |
|---|---|
| Strategy Pattern | Avoid switch statements, make new methods pluggable |
| Router abstraction | Centralize routing, keep processors isolated |
| Specific failure codes | More useful than generic FAILED |
| Simulated latency | Allows testing of resilience behavior |

---

## Phase 5 — Resilience & Fault Tolerance
**Status:** 🟢 Completed
**Branch:** `phase/5-resilience`

### Planned work (draft)
- Add Resilience4j dependency
- Apply circuit breaker to bank calls
- Add retry and timeout to downstream requests
- Add rate limiter if needed around Mock Bank
- Add dead-letter topics for failed Kafka processing
- Add DLT monitor endpoint

### Planning notes
- The system should tolerate flaky downstream services
- Failed Kafka messages should not disappear silently
- DLT monitor should show stuck messages quickly

### Planned checklist
- ✅ Resilience4j added
- ✅ Circuit breaker on bank integration
- ✅ Retry on transient failures
- ✅ Timeout set for slow calls
- ✅ DLT topics created
- [ ] `GET /admin/dlt-messages` added
- ✅ Circuit breaker behavior tested
- ✅ Recovery after service restart verified

### Decisions
| Decision | Reason |
|---|---|
| Resilience4j annotations | Keep business logic clean, declarative failure handling |
| 5 failures / 10s threshold | Balance sensitivity with stability |
| 3 retries with 500ms backoff | Handle transient failures without flood retrying |
| 3s timeout | Fail fast on slow bank response |
| DLT monitor | Operational visibility for failed message recovery |

### Implementation differences and decisions:
Why @RetryableTopic Instead of @Retry for Consumers: Payments are safety-critical and cannot afford message loss mid-retry. @RetryableTopic persists retry attempts in Kafka topics, surviving application crashes, while @Retry keeps retries in-memory only. Additionally, @RetryableTopic provides native DLT integration for automatic dead-letter routing after exhausted retries, visibility into retry topics for monitoring, and eventual consistency guarantees via Kafka durability—essential for financial transactions.
Why No Timeout on PaymentEventProducer: The Outbox Pattern already guarantees durability: payment data and outbox events are safely persisted in the database before any Kafka publish attempt, and OutboxPublisherJob retries every 5 seconds indefinitely until success. Adding @TimeLimiter would be redundant since it requires async return types (incompatible with void methods), @Retry already handles transient failures with backoff, and the Circuit Breaker detects Kafka broker outages anyway. The combination of database durability + Circuit Breaker + Retry is sufficient without timeout.

---

## Phase 6 — Settlement Service
**Status:** ⬜ Not Started
**Branch:** `phase/6-settlement`

### Planned work (draft)
- Build separate Settlement Service app
- Add Flyway migration for settlements table
- Implement Spring Batch job for end-of-day settlement
- Add scheduled trigger at 11:59 PM
- Add manual admin trigger for testing
- Mark payments SETTLED after settlement write

### Settlement flow
`Read SUCCESS payments not settled → group by merchant → sum amounts → write settlement record → mark payments SETTLED`

### Planning notes
- Job should be idempotent if rerun same day
- Chunk size should be limited to control memory/DB load
- Settlement record needs merchant, date, total, count, status

### Planned checklist
- [ ] Settlement Service started
- [ ] Flyway table migration added
- [ ] Spring Batch job configured
- [ ] Chunk size 100 implemented
- [ ] Daily cron trigger added
- [ ] Idempotent rerun behavior verified
- [ ] `GET /settlements?merchantId=X&date=Y` added
- [ ] Payments marked SETTLED after batch
- [ ] Manual trigger endpoint added

### Decisions
| Decision | Reason |
|---|---|
| Separate service | Keep batch work separate from real-time APIs |
| Spring Batch | Use framework semantics for retries and restarts |
| Chunk size 100 | Tradeoff between DB load and memory usage |
| Idempotent design | Prevent duplicate settlement records |
| Nightly batch | Standard payment settlement cadence |

---

## Phase 7 — Observability & Ops
**Status:** ⬜ Not Started
**Branch:** `phase/7-polish`

### Planned work (draft)
- Add Spring Cloud Gateway entry point
- Add tracing with Micrometer/Zipkin
- Expose Prometheus metrics
- Add Grafana dashboard notes/screenshots
- Add OpenAPI docs for APIs
- Add architecture overview to README

### Planning notes
- Need single ingress point and API routing
- Trace IDs should carry across service calls
- Metrics should cover throughput, success rate, latency, and consumer lag

### Planned checklist
- [ ] Spring Cloud Gateway wired
- [ ] API key rate limiting added
- [ ] Tracing enabled across services
- [ ] Prometheus actuator metrics exposed
- [ ] Grafana dashboard documented
- [ ] Swagger/OpenAPI added
- [ ] Architecture diagram added
- [ ] Design decisions documented
- [ ] Bruno request collection committed
- [ ] Grafana screenshot captured

### Decisions
| Decision | Reason |
|---|---|
| Spring Cloud Gateway | Single entry point, centralized routing |
| Zipkin tracing | Track distributed request paths across services |
| Prometheus + Grafana | Standard monitoring stack for metrics/alerts |
| Swagger auto-generation | Keep API docs in sync with code |
| Document architecture | Useful for ops and debugging |

---

## Timeline
- Phase 1: completed
- Phase 2: 3 weeks (4–6)
- Phase 3: 3 weeks (7–9)
- Phase 4: 3 weeks (10–12)
- Phase 5: 4 weeks (13–16)
- Phase 6: 4 weeks (17–20)
- Phase 7: 4 weeks (21–24)

## Mini projects
| Phase | Mini project | Estimate |
|---|---|---|
| Phase 1 | RestClient mini: two Spring apps, HTTP call | 2–3 days |
| Phase 2 | Kafka mini: produce, consume, crash, recover | 4–5 days |
| Phase 3 | Webhook mini: POST to webhook.site, retry | 2–3 days |
| Phase 5 | Resilience4j mini: circuit breaker on flaky endpoint | 2–3 days |
| Phase 6 | Spring Batch mini: read CSV, process, write DB | 3–4 days |
| Phase 7 | Docker mini: containerize service with Postgres+Redis | 3–5 days |

## Notes
- Keep phase work in separate branch
- Track decisions in `DECISIONS.md`
- Keep the repo operational, not just demo-ready
- Focus on correctness and recoverability over polish
