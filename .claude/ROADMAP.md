# ROADMAP.md — Phase 7-9 plan

This supersedes the original roadmap PDF's Phase 7 scope and `PROGRESS.md`'s "Phase 7 — Observability & Ops" label with a revised 3-phase plan, decided 2026-07-04:

- **Phase 7 — Cleanup, Consolidation & Containerization**: close every pending item found across all six services, plus the original Phase 7 observability/polish goals, and get the whole system running via a single `docker compose up`.
- **Phase 8 — Customer-Centric Additions**: the checkout/customer flow this system currently has no concept of (see below), plus a React frontend and an analytics dashboard.
- **Phase 9 — AWS Free-Tier Deployment**: originally slated as Phase 8; pushed back because there's nothing worth deploying to a real host until Phase 7's containerization and cleanup land.

`PROGRESS.md` still says "Phase 7 — Observability & Ops" from the original plan — that label predates this revision and hasn't been edited to match (flag if you want that updated too; it's your build journal, so left alone by default).

Per-service detail lives in each service's `.claude/PENDING.md`; this file is the map, not the territory. Sources throughout: `PROGRESS.md`, `distributed_payment_gateway_roadmap(11).pdf`, and direct code analysis (verified against source, not just the docs).

---

## Phase 7 — Cleanup, Consolidation & Containerization

### 7a. Per-service cleanup (full detail in each service's `PENDING.md`)

| Service | Must-fix before Phase 7 is done |
|---|---|
| `payment-service` | Outbox idempotency for `payment.initiated` *and* `payment.succeeded`; auth filter fails open on missing `Authorization` header; dead `jwt.*`/`auth.*`/`mock_bank.url` config; stale Redis cache on status update; no active tests |
| `mock-bank-service` | Wire up `BankConfig` so success-rate/latency are actually configurable (roadmap explicitly required this); add idempotency check on the consumer; Resilience4j fully unused (no rate limiter, no circuit breaker); dead code (`BankPaymentMethod`, `ACCOUNT_BLOCKED`) |
| `merchant-service` | **No auth on its own endpoints at all** — anyone can register/rotate/read merchants; `MerchantStatus` never enforced; no Swagger; dead Kafka/security-crypto dependencies |
| `webhook-service` | `@RetryableTopic` is dead code (listener swallows all exceptions — a merchant-service blip silently drops the webhook trigger with zero trace); no delivery idempotency/dedup; no distributed lock on the retry job; configured-but-unused `@Retry`/`@TimeLimiter`; no signature verification |
| `settlement-service` | No REST API at all (query + manual trigger both missing); "chunk size 100" and "idempotent reruns" claimed done in `PROGRESS.md` but not fully true in code (tasklet-based, not chunked; uncaught constraint violation on same-day reruns with new payments); **payment-service has no `SETTLED` status at all**, so "payments marked SETTLED" only means something inside settlement-service's own mirror table |
| `gateway-service` | Hardcoded `localhost` routes (won't survive containerization — fix this before 7c); no auth, no rate limiting, no circuit breaker at the gateway; only GET/POST routed |

### 7b. Cross-cutting / structural fixes (span multiple services)

- **No shared library for cross-service contracts.** `MerchantResponse`, `MerchantStatus`, `PaymentMethod`, `FailureReason`, `TransactionStatus`, `CurrencyCode` are all independently redefined in every service that needs them. No compiler-enforced way to catch drift when one service's contract changes — the single most impactful structural risk as the system grows, and worth fixing before Phase 8 adds more cross-service surface area.
- **`.env` files across nearly every service define variables that are never actually read by any code** (topic names, retry tuning constants, alternate service URLs) — a recurring pattern, not a one-off mistake.
- **Dead-letter topics have no consumer or monitor anywhere in the system** — the `.DLT` pattern is applied consistently (a genuine strength), but the visibility layer it's supposed to enable doesn't exist.
- **Testing is nearly absent system-wide** — every service has exactly one test class, and every one is a bare `@SpringBootTest` context-load check with no real assertions. Three of the six (`payment-service`, `mock-bank-service`, `webhook-service`) have that one test marked `@Disabled`; the other three (`merchant-service`, `settlement-service`, `gateway-service`) run it but it only checks the context boots.
- **Security posture is uneven.** `payment-service` has a real (if imperfect) API-key check; `merchant-service` — the service that *issues* those API keys — has none at all; `gateway-service` has none either. A coherent, single security model doesn't exist yet.

### 7c. Docker containerization

Containerize all six Spring Boot services and add them to `docker-compose.yml` (today it only provisions infra: 4× Postgres, Redis, Zookeeper, Kafka — no app containers exist). Prerequisites, in order:

1. **Externalize inter-service URLs** — every Feign client and the gateway's routes are hardcoded to `http://localhost:<port>`. This has to change to Docker service names (or env-driven values) before containerizing, or services won't be able to reach each other.
2. **Add topic provisioning** — `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`; nothing creates `payment.initiated`/`payment.processed`/`payment.succeeded` (or their retry/DLT variants) today. Add a script or init container so a fresh `docker compose up` doesn't require manual `kafka-topics --create` calls.
3. Write a `Dockerfile` per service (simple — Gradle build → JRE base image) and add each as a compose service, wired to the existing infra containers via service-name DNS.
4. Verify the full system boots end-to-end via `docker compose up` alone, with no manual `bootRun` steps — this is the acceptance bar for 7c being done, and the acceptance bar Phase 9 depends on.

### 7d. Observability & polish (the original Phase 7 scope — still applies)

- **Distributed tracing** (Micrometer + Zipkin) — no trace ID flows across the payment→bank→webhook/settlement chain today.
- **Prometheus metrics + Grafana dashboard** — payments/sec, success rate, p99 latency, circuit-breaker state, Kafka consumer lag, per the roadmap's target dashboard.
- **API-key rate limiting at the gateway** — roadmap targets 100 req/min per merchant.
- **Swagger/OpenAPI on all services** — only `payment-service`/`mock-bank-service` have it today.
- **`GET /admin/dlt-messages`** — a dead-letter monitor endpoint; every service already produces `.DLT` topics, only the visibility layer is missing.
- **Architecture diagram in README**, **`DECISIONS.md`**, **Bruno request collection** — none exist yet.
- **Error handling improvements** (general) — payment-service leaks raw exception messages, merchant-service returns 500 for not-found/bad-id, webhook-service silently swallows consumer exceptions.

### 7e. Suggested execution order within Phase 7

1. Extract the shared contracts library (7b) — do this early since it touches every service and is easiest before more surface area (Phase 8) gets added.
2. Per-service cleanup (7a) — each is independent, can be parallelized across sessions.
3. Externalize URLs + topic provisioning (7c, steps 1-2) — required groundwork.
4. Delete or wire up dead `.env`/config repo-wide while you're touching each service anyway.
5. Add a real test suite (Testcontainers-based, per service) — do this alongside 7a's fixes so the fixes are actually verified, not just asserted.
6. Containerize (7c, steps 3-4).
7. **Then, and only then**, tackle 7d's observability stack — tracing a request across services that aren't yet reliably network-reachable to each other (pre-7c) is of limited value, and chasing dashboards before the underlying data model is sound (pre-7a) just visualizes bugs.
8. Write `DECISIONS.md` and the architecture diagram as living documents throughout, not a one-time end-of-phase task — several discrepancies found in this review (settlement's chunk size, the webhook backoff schedule, the settlement cron time) exist precisely because a decision was made once and never revisited against what shipped.

---

## Phase 8 — Customer-Centric Additions

### 8a. The customer-facing checkout flow (backend)

**Scope note, not an oversight**: grepping the whole repo for any trace of customer identity or payment-credential capture (`customer`, `cardNumber`, `cvv`, `upiId`/`vpa`, `redirectUrl`, `checkout`) turns up nothing but `FailureReason.CVV_MISMATCH` — an enum label for a simulated decline reason, not real CVV data. No service has a `Customer` entity, a card/UPI/bank-account field, a hosted checkout page, or a session distinguishing "merchant creates a payment" from "customer supplies payment credentials." This was a reasonable scope cut for a backend-systems learning project (real card capture means real PCI-DSS scope), but it was implicit rather than a stated decision — Phase 8 is where that changes.

A production gateway (Razorpay/Stripe-style) has four actors: `Customer → Merchant backend creates an order (server-to-server) → Gateway returns a hosted checkout page/SDK → Customer enters real payment credentials directly into the gateway's surface (merchant never touches raw card data) → Gateway processes with the bank → result returns to the customer's browser (redirect) AND to the merchant's backend (webhook)`. This system today implements only the merchant-backend↔gateway call and the webhook notification — never the customer-facing middle.

**A minimal sketch that stays entirely out of PCI scope** (since `mock-bank-service` never touches a real card network, this only needs to *model* the interaction shape, not handle real data):

1. **Split "create" from "capture."** `POST /payments` still creates the record (merchant → gateway, no customer involved), but instead of immediately kicking off the outbox/Kafka flow, `Payment` starts in a new `CREATED` state. The response gains a `checkoutUrl` (e.g. `.../checkout/{paymentId}`); `PaymentRequest` gains a merchant-supplied `returnUrl`.
2. **A mock checkout endpoint** — `GET /checkout/{paymentId}` (shows amount + merchant name) and `POST /checkout/{paymentId}/confirm`, where the customer's browser submits fake, unvalidated per-method placeholder data (a 16-digit string for `CARD`, a `name@bank` string for `UPI`, a bank name for `NET_BANKING`) plus optional `customerEmail`/`customerName`. This confirm call flips `Payment` from `CREATED` to `PENDING` and triggers the *existing* outbox → `payment.initiated` → mock-bank-service flow — the machinery you already built becomes the "capture" backend, now triggered by a customer action instead of the merchant's original call.
3. **Getting the result back to the browser** — a `GET /checkout/{paymentId}/status` the browser polls until `SUCCESS`/`FAILED`, then redirects to the merchant's `returnUrl`. The merchant's backend should still treat the webhook as the source of truth, not the redirect (a customer can close the tab, a redirect can be spoofed) — this is exactly why real gateways send both.
4. **New fields on `Payment`**: `customerEmail`/`customerName` (nullable), `returnUrl`, and a `CREATED` value ahead of `PENDING` in `PaymentStatus`. No payment-credential fields need to be *persisted* — the fake card/UPI/bank strings only exist transiently in the checkout request.
5. **Where it lives**: start as a `checkout` package inside `payment-service` (reuses the existing entity/Kafka flow). Consider splitting into its own front-door surface (mirroring how Stripe Checkout/Razorpay's hosted page are genuinely separate from the core payments API) once `gateway-service` is less of a routing skeleton, or as a `checkout-service` — only worth it if "independently deployable/scalable from the payments API" becomes a point worth demonstrating.
6. **What this newly teaches**: intent-vs-capture separation, redirect-based flows, and — the most production-relevant lesson — reconciling two channels of truth for the same event (browser redirect vs. webhook), including what happens when they disagree. None of the original 7-phase roadmap covers this.

### 8b. React frontend

Two distinct surfaces, worth keeping conceptually separate even if built in the same React app initially:

- **Customer checkout UI** — the browser-facing form for 8a's `GET/POST /checkout/{paymentId}` endpoints. Small, single-purpose (amount + merchant name + fake payment-method form + a spinner while polling status).
- **Merchant dashboard** — a real UI over the admin/query endpoints that already exist: payments list/detail (`GET /payments/admin`, `GET /payments/admin/id/{id}`, `GET /payments/admin/status/{status}`), webhook delivery status and manual retry (`GET /webhooks/status-pending`, `GET /webhooks/status-dead`, `POST /webhooks/retry/{id}`), and settlement history once settlement-service's query endpoint exists (7a). A merchant self-service view (register, view/rotate API key) would also live here — **this can't ship safely until merchant-service actually has authentication (7a)**, so building this dashboard is a good forcing function to make sure that fix actually landed.

### 8c. Analytics dashboard

No aggregate/analytics endpoint exists anywhere in the system today — every existing endpoint is paginated CRUD-style listing, nothing computes a rate, a trend, or a rollup. Two layers, sequenced deliberately:

- **Cheap first pass, buildable now**: new aggregate endpoints backed by plain SQL over existing tables — payments per day, success rate overall and by payment method (data already sits in `payments.status`/`payments.payment_method`), settlement totals per merchant per period (from settlement-service's `settlements` table), webhook delivery health (delivered/failed/dead counts over time, from `webhook_deliveries`), and a rough "payments stuck in PENDING longer than N seconds" proxy for consumer lag.
- **The "real" version, once Phase 7d lands**: p99/p95 latency and true Kafka consumer lag need actual Prometheus histograms/Kafka Exporter data, not a DB query — build the cheap version first so the dashboard has something to show, then swap in Prometheus-backed panels (or embed Grafana directly) once 7d is done.

---

## Phase 9 — AWS Free-Tier Deployment

Kept intentionally brief — most of these are decisions to make once you're actually here, not now. The main constraint to plan around: AWS free tier is a single small compute instance and a single small managed database, not a fleet.

- **Compute**: one EC2 `t2.micro`/`t3.micro` (750 free hours/month) running the Phase 7c `docker compose` setup directly. Realistically, 6 JVM services + Kafka + Zookeeper + 4 Postgres containers will not comfortably fit in a free-tier instance's ~1GB RAM — plan to consolidate the 4 separate Postgres containers into one Postgres instance with 4 databases/schemas before this phase, purely to fit the memory budget (this is a deployment-driven change, not a code architecture change).
- **Database**: RDS free tier (`db.t3.micro`, single instance, 20GB storage, 750 hours/month) covers one Postgres instance — consistent with the consolidation above. Only one instance is free; running 4 would exceed the free tier.
- **Kafka**: AWS's managed Kafka (MSK) is *not* free-tier eligible and is expensive — keep Kafka self-hosted in Docker on the same EC2 instance rather than switching to a managed service. (Swapping to SQS/SNS to dodge this cost would lose the consumer-group/replay semantics that were the whole point of Phase 2 — not recommended.)
- **Secrets**: move from `.env` files to AWS Systems Manager Parameter Store (free tier eligible for standard parameters) rather than shipping `.env` files to a public instance.
- **Networking**: an Elastic IP (free while attached to a running instance) is enough; a custom domain via Route 53 is *not* free (hosted zone + registration cost) — worth flagging so it's a conscious spend, not a surprise.
- **CI/CD**: GitHub Actions (free minutes on a public/personal repo) building images, pushed to ECR (free tier: ~500MB-1GB storage for 12 months), pulled and restarted on the EC2 instance via SSH or a simple webhook.
- **React frontend hosting**: host separately from the backend EC2 instance for free — S3 (5GB free) + CloudFront (1TB/month free for 12 months), or AWS Amplify's free tier. Decoupling this from the backend instance keeps the EC2 box's limited resources for the Spring Boot services/Kafka/Postgres.
- **Acceptance bar for starting Phase 9**: Phase 7c's `docker compose up` must already work standalone and reliably — Phase 9 is "take the thing that already runs correctly and put it on a small box," not "debug containerization for the first time in production."
