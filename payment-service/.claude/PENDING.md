# PENDING.md — payment-service

Tracks outstanding work for this service, consolidated from `PROGRESS.md`, the original roadmap PDF (`distributed_payment_gateway_roadmap(11).pdf`), and direct code analysis. See `.claude/CLAUDE.md` (this service and root) for architecture context behind each item.

## From PROGRESS.md

- **Idempotency on Outbox publishing** (Phase 3 cleanup, unchecked) — prevent duplicate Kafka publishes if `OutboxPublisherJob` crashes after a Kafka ack but before the DB status commit. Currently a crash in that window can double-publish `payment.initiated`.
- **Outbox pattern for the `payment.succeeded` flow** (Phase 6 checklist, unchecked) — `PaymentSucceededProducer.publishPaymentSucceeded` is a bare circuit-breaker-wrapped Kafka send with no outbox backing it. A producer/broker failure here just logs and drops the event permanently (unlike `payment.initiated`, which retries forever via the outbox).
- **Error handling improvements** (Phase 3 cleanup, "General", unchecked) — graceful degradation and better error messages system-wide; in this service specifically, `GlobalExceptionHandler`'s catch-all leaks raw exception messages to clients.
- **API documentation (Swagger/OpenAPI on all endpoints)** (Phase 3 cleanup + Phase 7 checklist, unchecked) — already done for this service (`springdoc-openapi-starter-webmvc-ui`, `@Tag`/`@Operation` on `PaymentController`); tracked here because the umbrella item is still open system-wide (see root summary).
- **`GET /admin/dlt-messages`** (Phase 5 checklist, unchecked) — no dead-letter monitor endpoint exists anywhere; `payment.processed.DLT` messages accumulate unconsumed with no visibility.

## From the roadmap PDF

- **Rate Limiter** — listed as one of the four core Resilience4j patterns ("limit calls per second to protect downstream services... prevent overloading Mock Bank"). Never implemented anywhere in this service (only Circuit Breaker exists, on the two Kafka producers).
- **Phase 7 — Spring Cloud Gateway, tracing, Prometheus, Grafana, Swagger-on-all-services, architecture diagram, DECISIONS.md, Bruno collection** — all system-wide Phase 7 items; this service's specific slice is: expose its Actuator metrics for Prometheus scraping, and propagate/accept a trace ID once Micrometer+Zipkin is added. None of this exists yet in `payment-service` (no `micrometer-tracing`/`micrometer-registry-prometheus` dependency, no custom `management.endpoints` exposure beyond defaults).
- **"What happens if Mock Bank takes 30 seconds to respond?"** (Phase 1 interviewer-question prompt) — answered structurally by the Kafka-based design (no synchronous call to mock-bank-service exists anymore), but worth confirming: if `mock-bank-service`'s Kafka consumer is slow/stuck, `payment-service` has no visibility into that beyond eventually seeing no `payment.processed` event — there's no timeout/alert on "payment stuck in PENDING too long."

## Holistic gaps (missed by both PROGRESS.md and the roadmap)

- **Auth filter fails open**: `ApiKeyValidationFilter` only checks the API key when an `Authorization: Bearer ...` header is present — if the header is missing entirely, the request proceeds with `merchantId=null`, and the resulting `NOT NULL merchant_id` constraint violation is misdiagnosed by the idempotency race-guard as a duplicate insert, surfacing as a confusing generic `500` instead of a clean `401`. Fix should be structural (reject if the header is absent for `/payments` writes), not just cosmetic.
- **Dead configuration never cleaned up**: `jwt.secret`, `jwt.expiration`, `auth.user.*`, `auth.admin.*`, `mock_bank.url` in `application.yml`/`.env` are read by nothing in the codebase — leftover from the Phase 1 JWT design and the pre-Kafka HTTP call to mock-bank-service.
- **`.env.example` is out of sync**: missing `AUTH_*`, `KAFKA_*`, `MOCK_BANK_URL` placeholders that either `application.yml` or the real `.env` reference — a new contributor following the README's `cp .env.example .env` instruction gets an incomplete file.
- **`publishToDeadLetterFallback` doesn't dead-letter anything** — both Kafka-producer circuit-breaker fallbacks (in `PaymentEventProducer` and `PaymentSucceededProducer`) just log and fail the future; the method name implies a DLT write that doesn't exist.
- **`PaymentCacheService.evict()` is defined but never called** — the Kafka consumer that flips a payment's status (`PaymentProcessedConsumer`) doesn't evict/refresh the Redis cache, so a client polling `GET /payments/admin/id/{id}` right after the status change can see a stale cached `PENDING` response for up to the 24h TTL.
- **Hardcoded Feign URL** (`http://localhost:8082`) to merchant-service — no env var, no service discovery; breaks outside a single-host dev setup.
- **No active tests** — the only test class (`PaymentServiceApplicationTests`) is `@Disabled`. Zero coverage of idempotency logic, the outbox job, the Kafka consumer's idempotency guard, or the auth filter.
- **No topic-provisioning** — `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false` in `docker-compose.yml`, but nothing in this service creates its topics (`payment.initiated`, `payment.succeeded`) programmatically; a fresh environment requires manual `kafka-topics --create` calls.

## Additional Improvements (suggested future roadmap)

- Give the outbox row an idempotency/dedup key (e.g. store the Kafka `SendResult`'s offset or a publish-attempt UUID) so a crash between ack and commit can be detected and doesn't double-publish.
- Add a real dead-letter **table** (not just a log line) that both circuit-breaker fallbacks write to, feeding the still-unbuilt `GET /admin/dlt-messages` endpoint.
- Evict (or actively refresh) the Redis cache entry from `PaymentProcessedConsumer` when a payment's status changes, instead of relying purely on TTL expiry.
- Fix the auth filter to reject (401) any `/payments` write request with no `Authorization` header at all, rather than silently letting it through.
- Delete the dead `jwt.*`/`auth.*`/`mock_bank.url` config and the corresponding unused `.env` entries; regenerate `.env.example` from what `application.yml` actually requires.
- Externalize the merchant-service base URL (env var or Eureka/Consul-based discovery) instead of a hardcoded `localhost:8082`.
- Add a Testcontainers-based integration test suite (Postgres + Kafka + Redis) covering: idempotent payment creation under concurrent requests, the outbox publish/retry loop, and the `PaymentProcessedConsumer`'s duplicate-event guard.
- Consider whether `payment.succeeded` needs outbox-level durability or whether an at-least-once-with-monitoring approach is an acceptable, cheaper trade-off — document the decision either way in the still-unwritten `DECISIONS.md`.
- Add a Prometheus counter/timer around the outbox publish loop and Kafka consumer processing, ahead of the full Phase 7 observability push, so there's already a signal for "payments stuck in PENDING."
