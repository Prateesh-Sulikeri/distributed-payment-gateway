# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Service overview

`merchant-service` owns merchant registration and API-key issuance/validation. Port **8082**, own Postgres (`merchant_db`, host port 5433). It is a pure "callee" in this system: it never calls any other service, it only serves inbound REST calls from `payment-service` and `webhook-service`.

See the repo-root `.claude/CLAUDE.md` for the system-wide event flow and other services.

## Commands

```bash
./gradlew build
./gradlew test
./gradlew test --tests "com.payments.merchant_service.MerchantServiceApplicationTests"
./gradlew bootRun    # needs merchant-db reachable (docker-compose service merchant-db, host port 5433)
```

Base package: `com.payments.merchant_service`.

## Architecture

```
controller/ MerchantController — @RequestMapping("/merchants")
dto/        MerchantRequest, MerchantResponse
entity/     Merchant — JPA entity, table `merchants`
type/       MerchantStatus (ACTIVE, INACTIVE, SUSPENDED)
mapper/     MerchantMapper — manual (no MapStruct)
repository/ MerchantRepository
security/   ApiKeyGenerator — key generation/hash/verify
service/    MerchantService (interface) + impl — MerchantServiceImpl
```

No `config/`, `exception/`, or Spring Security filter-chain package exists. There is **no `@ControllerAdvice`/global exception handler** in this service.

## Domain model — `Merchant` entity

```
id            UUID, @GeneratedValue(UUID)
name          String, not null
email         String, not null, unique
apiKeyHash    String, not null (DB-level UNIQUE via Flyway; @Column says unique=false — annotation/schema
              mismatch, harmless since ddl-auto=validate never regenerates constraints)
webhookUrl    String, nullable
status        MerchantStatus, not null (ACTIVE/INACTIVE/SUSPENDED)
createdAt     Instant, @CreationTimestamp
updatedAt     Instant, @UpdateTimestamp
apiKey        String, @Transient — carries the raw/plaintext key only right after
              generation/rotation; never persisted, never populated when loaded from DB
```

Flyway (`V1__create_merchants_table.sql`) is the only migration — single active API key per merchant (schema/model has no support for multiple keys).

## API endpoints (`MerchantController`, base `/merchants`)

| Method | Path | Notes |
|---|---|---|
| `POST` | `/register` | Returns `201` + `MerchantResponse` with plaintext `apiKey` (only time it's returned besides rotate). **No `@Valid`** on the request body — `MerchantRequest`'s `@AssertTrue isValid()` constraint is never actually enforced. |
| `GET` | `/validate-key?apiKey=...` | The endpoint other services call to validate a merchant's API key. `200` + `MerchantResponse` (apiKey null) if found, else bare `401`. |
| `GET` | `/{id}` | Throws bare `RuntimeException("Merchant not found")` if missing → surfaces as **500**, not 404 (no `@ControllerAdvice` exists to map it). |
| `PUT` | `/{id}` | Partial update (only non-blank fields applied). No `@Valid` either. `IllegalArgumentException` on bad id → also surfaces as 500. |
| `POST` | `/api-keys/rotate/{id}` | Generates a new key/hash, overwrites the merchant's only slot — old key is invalidated immediately, no grace/overlap period. |

**No auth on merchant-service's own endpoints** — no Spring Security dependency, no filter chain. Anyone with network access can register merchants, fetch merchant details, or rotate a merchant's key.

## API key mechanics (`security/ApiKeyGenerator`)

- `generateApiKey()`: literal prefix `"sk_live"` concatenated directly with a random UUID with dashes stripped — **no separator** between prefix and value (e.g. `sk_live3fa85f64...`).
- `hashApiKey(raw)`: SHA-256 digest, Base64-encoded. **No salt, no pepper, not BCrypt/Argon2/PBKDF2** — a fast unsalted hash. Acceptable given the input is a high-entropy random key (not a low-entropy password), but note `spring-security-crypto` is on the classpath **unused** — hashing is hand-rolled with `java.security.MessageDigest` instead.
- `verify(raw, hash)`: recomputes and does a plain (non-constant-time) `String.equals`.
- `validateApiKey(rawApiKey)` in `MerchantServiceImpl` hashes the incoming key and looks it up via `MerchantRepository.findByApiKeyHash` — this is the single method backing `/validate-key`.

## Known gaps (verified — don't assume these work)

- **`MerchantStatus` is never enforced.** Set to `ACTIVE` at registration and never read/branched-on anywhere else — `validateApiKey` only checks hash match, not status. A `SUSPENDED`/`INACTIVE` merchant's key still validates successfully today. (Root `PROGRESS.md` lists "Merchant status management (ACTIVE/INACTIVE/SUSPENDED enforcement)" as an open TODO.)
- **No `@Valid` anywhere** in the controller — Bean Validation annotations on `MerchantRequest` are inert.
- **Inconsistent/absent exception handling** — bare `RuntimeException`/`IllegalArgumentException` for not-found/bad-id cases surface as generic 500s, not 404/400.
- **Dead dependencies/config**: `spring-boot-starter-kafka` is declared but **no Kafka producer/consumer code exists anywhere** in this service (likely reserved for future merchant-lifecycle events). `spring-security-crypto` is declared but unused. The `.env`'s `JWT_SECRET`/`JWT_EXPIRATION`/`AUTH_USER_*`/`AUTH_ADMIN_*` variables are **not referenced by any code** — leftover boilerplate, not active auth.
- **Only one test exists** (`MerchantServiceApplicationTests`, empty `contextLoads()`), despite `-data-jpa-test`/`-flyway-test`/`-webmvc-test` starters being present as test dependencies — essentially a blank slate for future test coverage.
- **Multiple API keys per merchant** is not supported (schema/model only allow one non-nullable unique hash column) — also an open `PROGRESS.md` TODO.

## Configuration (`application.yml`, single profile)

```yaml
server.port: ${MERCHANT_PORT:8082}
spring.datasource: url/username/password from MERCHANT_DB_URL/MERCHANT_DB_USERNAME/MERCHANT_DB_PASSWORD
spring.jpa.hibernate.ddl-auto: validate   # Flyway owns the schema
spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}   # unused, no Kafka code
spring.flyway.locations: classpath:db/migration
```
`.env` provides `MERCHANT_PORT`, `MERCHANT_DB_URL` (`jdbc:postgresql://localhost:5433/merchant_db`), `MERCHANT_DB_USERNAME`/`PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, plus unused `JWT_*`/`AUTH_*` vars (see Known Gaps). No `.env.example` template is checked in for this service.

## How other services integrate with merchant-service

Both consuming services use **Spring Cloud OpenFeign** with a **hardcoded** base URL `http://localhost:8082` (no service discovery):

- **`payment-service`** — `MerchantServiceClient.validateApiKey(apiKey)` → `GET /validate-key`, called from `ApiKeyValidationFilter` on every `/payments/**` request that supplies a `Bearer` token.
- **`webhook-service`** — `MerchantServiceClient.getMerchantById(id)` → `GET /{id}`, used to fetch `webhookUrl` for delivery.

Each consuming service maintains its **own independently duplicated copy** of `MerchantResponse` DTO and `MerchantStatus` enum rather than sharing a library — if this service's response contract changes, both consumers need manual updates.
