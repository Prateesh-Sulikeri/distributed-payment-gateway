# PENDING.md — merchant-service

Tracks outstanding work for this service, consolidated from `PROGRESS.md`, the original roadmap PDF (`distributed_payment_gateway_roadmap(11).pdf`), and direct code analysis. See `.claude/CLAUDE.md` (this service and root) for architecture context behind each item.

## From PROGRESS.md

- **Multiple API keys per merchant support** (Phase 3 cleanup, unchecked) — schema and entity only support one active `apiKeyHash` column; rotating a key immediately invalidates the previous one with no overlap/grace period.
- **Merchant status management (ACTIVE/INACTIVE/SUSPENDED enforcement)** (Phase 3 cleanup, unchecked) — `MerchantStatus` is set to `ACTIVE` at registration and never read again. `MerchantServiceImpl.validateApiKey` only checks the hash match, not status — a `SUSPENDED`/`INACTIVE` merchant's key still validates successfully today.
- **Error handling improvements** (Phase 3 cleanup, "General", unchecked) — concretely in this service: `getMerchantById`/`updateMerchant`/`rotateApiKey` throw bare `RuntimeException`/`IllegalArgumentException` with no `@ControllerAdvice`, so "not found"/"bad id" surface as generic `500`s instead of `404`/`400`.
- **API documentation (Swagger/OpenAPI on all endpoints)** (Phase 3 cleanup + Phase 7 checklist, unchecked) — **not started here**: no springdoc/OpenAPI dependency or annotations exist in this service at all (unlike payment-service/mock-bank-service).

## From the roadmap PDF

- **"API Key auth — every payment request must include a valid merchant API key"** (Phase 3 goal) — the validation endpoint (`GET /merchants/validate-key`) exists and is consumed correctly by payment-service, but the roadmap's implicit expectation of a properly access-controlled merchant management surface is not met (see holistic gap below — merchant-service's *own* endpoints have no auth at all).
- **Phase 7 — Swagger on all services** — this is the service furthest behind on that specific item; no OpenAPI dependency exists yet.
- **Phase 7 — architecture diagram / design decisions documentation** — system-wide item; this service's merchant-registration and API-key lifecycle would be a natural candidate for a sequence diagram once the README's architecture diagram is built.

## Holistic gaps (missed by both PROGRESS.md and the roadmap)

- **merchant-service's own endpoints have no authentication or authorization at all** — no Spring Security dependency, no filter chain, no admin credential check anywhere. Anyone with network access to port 8082 can register merchants, fetch any merchant's details by ID (including their `webhookUrl`), or rotate any merchant's API key (which immediately invalidates the legitimate merchant's key). This is arguably the single biggest security gap in the whole system and isn't called out anywhere in `PROGRESS.md` or the roadmap, which only discuss API-key auth *for payment requests*, not for the merchant-management surface itself.
- **No `@Valid` enforcement anywhere** — `MerchantRequest`'s `@AssertTrue isValid()` Bean Validation constraint is dead code because the controller never annotates the request body with `@Valid`.
- **Dead dependencies**: `spring-boot-starter-kafka` is declared but no producer/consumer code exists anywhere in this service (no merchant-lifecycle events are published). `spring-security-crypto` is declared but unused — API key hashing is hand-rolled with `java.security.MessageDigest` instead.
- **Dead `.env` config**: `JWT_SECRET`, `JWT_EXPIRATION`, `AUTH_USER_*`, `AUTH_ADMIN_*` are defined but read by nothing in the codebase.
- **Only one test exists** (`MerchantServiceApplicationTests`, empty `contextLoads()`) — despite `-data-jpa-test`, `-flyway-test`, `-webmvc-test` all being present as test dependencies, none are used.
- **API key format has no separator** (`sk_live` + raw UUID hex with no delimiter) — cosmetic, but worth fixing alongside any future key-format work since it makes keys harder to visually parse/redact in logs.

## Additional Improvements (suggested future roadmap)

- **Add authentication to merchant-service's own management endpoints** before this system goes anywhere near a real deployment — at minimum HTTP Basic with the still-unused `AUTH_ADMIN_*` credentials already sitting in `.env`, ideally a proper API-key-for-admins or mTLS-between-services scheme once the gateway exists.
- Enforce `MerchantStatus` in `validateApiKey` — reject `SUSPENDED`/`INACTIVE` merchants explicitly, and add an endpoint (or extend `PUT /{id}`) to actually transition a merchant's status.
- Add a `@ControllerAdvice`/`GlobalExceptionHandler` (mirroring payment-service's pattern) to map not-found/bad-id exceptions to proper `404`/`400` responses.
- Support multiple concurrent API keys per merchant (e.g. a separate `merchant_api_keys` table keyed by merchant, each with its own status/expiry) so rotation can have a grace-period overlap instead of instantly invalidating the old key.
- Publish merchant lifecycle events (`MerchantRegistered`, `MerchantStatusChanged`, `MerchantWebhookUrlUpdated`) to Kafka — this would let `payment-service`/`webhook-service` react to status changes without a synchronous Feign round-trip on every request, and would finally give the declared-but-unused Kafka dependency a purpose.
- Add springdoc-openapi and annotate the controller, closing the Swagger gap relative to payment-service/mock-bank-service.
- Add real tests: unit tests for `ApiKeyGenerator` (hash determinism, format), `MerchantServiceImpl` (status enforcement once added, partial-update semantics), and a `@WebMvcTest`/Testcontainers-backed integration test for the controller + Flyway schema.
- Consider a constant-time comparison in `ApiKeyGenerator.verify()` (currently a plain `String.equals`) for defense-in-depth, even though the practical risk is low given API keys are high-entropy.
