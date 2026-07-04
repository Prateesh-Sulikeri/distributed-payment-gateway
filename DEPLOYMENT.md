# Deployment Guide

This document covers how to run the system today (Docker Compose, single host) and what changes when it eventually moves to AWS (Phase 9 — see `.claude/ROADMAP.md`). It was introduced as part of the Stage 4 infra/config refactor (2026-07-04), which containerized all six services and standardized their configuration.

## Environment variable convention

Every service owns an isolated `.env` (gitignored) and a checked-in `.env.example`. Because each service's `.env` is independent, variable names are **not** service-prefixed — the same names mean "this service's own X" in every `.env` file:

| Variable | Meaning | Used by |
|---|---|---|
| `SERVER_PORT` | The port this service listens on | all six |
| `SPRING_PROFILES_ACTIVE` | `dev` / `sit` / `prod` — selects `application-<profile>.yml` | all six |
| `DB_HOST` | Postgres host | payment, merchant, webhook, settlement |
| `DB_PORT` | Postgres port (container-internal port, always `5432` inside Docker regardless of the host-mapped port) | payment, merchant, webhook, settlement |
| `DB_NAME` | Database name | payment, merchant, webhook, settlement |
| `DB_USER` / `DB_PASSWORD` | Database credentials | payment, merchant, webhook, settlement |
| `REDIS_HOST` / `REDIS_PORT` | Redis connection | payment only |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker list | payment, mock-bank, webhook, settlement |
| `MERCHANT_SERVICE_URL` | Base URL for the merchant-service Feign client | payment, webhook, gateway |
| `PAYMENT_SERVICE_URL` / `WEBHOOK_SERVICE_URL` / `SETTLEMENT_SERVICE_URL` | Base URLs for the gateway's proxied routes | gateway only |
| `AUTH_ADMIN_USERNAME` / `AUTH_ADMIN_PASSWORD` / `AUTH_ADMIN_ROLE` | **Reserved, unused today** — kept in merchant-service's `.env` for a future admin-auth pass on its currently-open management endpoints | merchant (reserved) |

Local dev defaults (`${VAR:default}` in each `application.yml`) all point at `localhost` and the host-mapped ports from `docker-compose.yml`, so a service still boots with zero `.env` file present, matching its pre-refactor behavior. Docker Compose overrides these at the container level to Docker service-name hosts and container-internal ports (see `docker-compose.yml`'s `environment:` blocks) — no code change is needed to move between the two; only the env var *values* change.

## Spring profiles (dev / sit / prod)

Every service has `application-dev.yml`, `application-sit.yml`, `application-prod.yml` alongside the base `application.yml`. They only vary logging verbosity and `management.endpoint.health.show-details` (dev/sit: `always`, prod: `never`) — deliberately minimal, since environment-specific *values* (DB host, service URLs, etc.) are handled by env vars, not by profile-specific config. Select one via `SPRING_PROFILES_ACTIVE` in `.env`; Docker Compose sets it to `dev` for all six services by default.

## Running with Docker Compose

```bash
docker compose up -d --build
```

What happens, in order (enforced via `depends_on` + healthchecks, not just container start order):
1. Postgres ×4, Redis, Zookeeper, Kafka start; each Postgres/Redis/Kafka has a real healthcheck (`pg_isready`, `redis-cli ping`, `kafka-broker-api-versions`), not just "container running."
2. `kafka-topic-init` runs once Kafka reports healthy, creates `payment.initiated`, `payment.processed`, `payment.succeeded` (+ their `.DLT` topics) via `scripts/provision_kafka_topics.py`, then exits. `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false` means these topics would otherwise never exist.
3. `merchant-service` starts once its DB is healthy (it's a pure callee — nothing else needs to be up first).
4. `payment-service` and `webhook-service` start once their own DB/Kafka/topic-init dependencies are satisfied and `merchant-service` has at least started (Feign calls to it will retry/fail until it's actually accepting connections — no explicit health-gate on the Feign target itself yet).
5. `settlement-service` and `mock-bank-service` start once Kafka/topics are ready.
6. `gateway-service` starts last, once all four downstream services have at least started.

All six services keep their original host port mappings (8080–8084, 9000), so existing Bruno/Postman requests and browser bookmarks to `localhost:<port>` keep working unchanged.

### Kafka networking note

Kafka runs a dual-listener setup (`PLAINTEXT` on `kafka:29092` for container-to-container traffic, `PLAINTEXT_HOST` on `localhost:9092` for host-machine access) — this is why a Java process started via `bootRun` outside Docker and a containerized service can both reach the same broker without either one hardcoding the other's addressing scheme.

### Kafka topic provisioning outside Docker Compose

```bash
pip install -r scripts/requirements.txt
python scripts/provision_kafka_topics.py --bootstrap-servers localhost:9092
```

Idempotent — safe to re-run; it only creates topics that don't already exist. Every value (bootstrap servers, partition count, replication factor) is a flag or env var, so the same script/image runs unmodified against AWS MSK later by pointing `--bootstrap-servers` (or `KAFKA_BOOTSTRAP_SERVERS`) at the MSK broker endpoints.

## What's deliberately NOT done yet

- **No Spring Cloud Config Server or Eureka Discovery** — intentionally deferred. The env-var-driven config in every service (`${MERCHANT_SERVICE_URL:http://localhost:8082}`-style placeholders, `@FeignClient(url = "${merchant-service.url}")`, the gateway's `@Value`-injected route targets) was deliberately structured so that introducing either later is a small change: a Config Server just needs `spring.config.import=configserver:` and the local YAML values removed; Eureka just needs the explicit `url=` attributes dropped from the `@FeignClient` annotations (`name=` alone plus `@LoadBalanced` is enough once services register themselves).
- **No new authentication was added anywhere in this pass.** merchant-service's management endpoints remain unauthenticated (its `AUTH_ADMIN_*` env vars are reserved-but-unused for exactly this future work), and payment-service's `ApiKeyValidationFilter` was left exactly as-is (including its known fail-open gap on a missing `Authorization` header). The eventual model is Gateway Authentication + Service Authentication + a dedicated Authentication Service, not per-service ad hoc auth — see `.claude/ROADMAP.md`.
- **No Prometheus/Grafana/tracing yet** — every service now exposes `health`, `info`, `metrics`, and (as a placeholder) `prometheus` via Actuator, but the `micrometer-registry-prometheus` dependency itself hasn't been added anywhere, so `/actuator/prometheus` won't yet return real Prometheus-format output. This is Stage 6 work per `.claude/ROADMAP.md`.

## Path to AWS (Phase 9 — not started)

See `.claude/ROADMAP.md`'s Phase 9 section for the full plan. In short: this Docker Compose setup is meant to run largely unmodified on a single EC2 instance, with the 4 separate Postgres containers consolidated into one RDS instance (free-tier only covers one DB instance), Kafka kept self-hosted on the same EC2 box (MSK is not free-tier eligible), and `.env` files replaced by AWS Systems Manager Parameter Store. None of that is implemented yet — this document will be updated when it is.
