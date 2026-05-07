# Build Progress

## Phase 1 — Core Foundation
**Status:** 🟡 In Progress  
**Branch:** `phase/1-core-foundation`

### Features Completed
- ✅ Project setup & folder structure
- ✅ Docker Compose — Postgres + Redis
- ✅ Flyway migrations
- ✅ Payment entity, repository, DTOs
- ✅ Global exception handling
- ✅ `POST /api/v1/payments` with idempotency
- ✅ `GET /api/v1/payments/{id}`
- ✅ Redis caching with DB fallback
- ✅ Currency Validations added
- ✅Mock Bank Service
- ✅ RestClient with timeout
- ✅ Full flow tested in Bruno
- ✅ Logging + dedicated log file
- ✅ `.env` setup

### Key Decisions Made
| Decision | Why |
|---|---|
| UUID as primary key | Doesn't leak volume, safe for distributed systems |
| DECIMAL(19,4) for amount | Exact precision, never use float for money |
| Idempotency key as HTTP header | Separates request identity from payment data, follows Stripe convention |
| Redis as L1 cache with DB fallback | Redis failure never breaks payment flow |
| `DataIntegrityViolationException` guard | Handles race condition when two requests with same idempotency key arrive simultaneously |
| Flyway owns schema, not Hibernate | Two things should never manage your schema |
| Auth in payment service for now | Extracted to Auth Service in Phase 3 |

---

## Phase 2 — Kafka Async Processing
**Status:** ⬜ Not Started

---

## Phase 3 — Merchant Side & Webhooks
**Status:** ⬜ Not Started

---

## Phase 4 — Multiple Payment Methods
**Status:** ⬜ Not Started

---

## Phase 5 — Resilience
**Status:** ⬜ Not Started

---

## Phase 6 — Settlement Service
**Status:** ⬜ Not Started

---

## Phase 7 — Polish
**Status:** ⬜ Not Started
