# Docker + Postgres + Redis + Kafka Cheat Sheet

*For Spring Boot Microservice Development*

As of the Stage 4 infra/config refactor (2026-07-04), `docker compose up` runs the *entire* system — all six Spring Boot services plus infra — not just Postgres/Redis/Kafka. This sheet covers both. See [`DEPLOYMENT.md`](DEPLOYMENT.md) for the full environment-variable reference and startup sequence.

---

# 1. General Docker Commands

## Start everything (infra + all six services)

```bash
docker compose up -d --build
```

`--build` picks up any code/Dockerfile changes; drop it for a faster start if nothing changed.

---

## Start only infra (no app containers)

Useful if you want to run one or more services locally via `bootRun`/your IDE instead of in Docker:

```bash
docker compose up -d postgres merchant-db webhook-db settlement-db redis zookeeper kafka kafka-topic-init
```

---

## Start / rebuild a single service

```bash
docker compose up -d --build payment-service
```

Rebuilds and restarts just that one container; everything else keeps running untouched.

---

## Restart a service without rebuilding

```bash
docker compose restart payment-service
```

---

## Stop all services

```bash
docker compose down
```

---

## Stop + DELETE all volumes/data

⚠️ Dangerous. Deletes all 4 Postgres DBs + Kafka topics/messages + Redis data.

```bash
docker compose down -v
```

---

## Check status of every container (with health)

```bash
docker compose ps
```

Or, for a cleaner table:

```bash
docker compose ps --format "table {{.Name}}\t{{.Status}}"
```

App containers show `(healthy)` only after their Actuator `/actuator/health` check passes (`start_period: 45s`, then every 30s) — `Up X seconds` alone doesn't mean the app inside has finished booting.

---

## Check running containers (plain Docker, not Compose-scoped)

```bash
docker ps
docker ps -a   # include stopped containers
```

---

## Enter a container shell

```bash
docker exec -it payment_postgres bash
docker exec -it payment_redis sh
docker exec -it payment_kafka bash
docker exec -it payment-service sh
```

App-service containers run on `eclipse-temurin:*-jre-alpine`, so use `sh`, not `bash` (Alpine doesn't ship bash by default).

---

# 2. Microservice (App Container) Commands

Container names match the service directory names — no `payment_`-style prefix like the infra containers use.

| Service | Container name | Host port |
|---|---|---|
| payment-service | `payment-service` | 8080 |
| mock-bank-service | `mock-bank-service` | 8081 |
| merchant-service | `merchant-service` | 8082 |
| webhook-service | `webhook-service` | 8083 |
| settlement-service | `settlement-service` | 8084 |
| gateway-service | `gateway-service` | 9000 |

## View logs for one service

```bash
docker logs payment-service
docker logs mock-bank-service
docker logs merchant-service
docker logs webhook-service
docker logs settlement-service
docker logs gateway-service
```

## Follow logs live (tail -f style)

```bash
docker logs -f payment-service
```

## Follow logs for every service at once, interleaved

```bash
docker compose logs -f
```

Or a subset:

```bash
docker compose logs -f payment-service mock-bank-service webhook-service
```

## Tail just the last N lines

```bash
docker logs --tail 100 payment-service
```

## Filter logs for a specific event (grep)

The `dev` profile (Compose's default `SPRING_PROFILES_ACTIVE`) logs at `DEBUG` for Kafka's client internals, which is noisy — filter to the actual business-logic log lines by package/class name:

```bash
docker logs webhook-service 2>&1 | grep -iE "PaymentProcessedConsumer|WebhookDeliveryServiceImpl" | grep -v DEBUG
docker logs settlement-service 2>&1 | grep -iE "PaymentSucceededConsumer" | grep -v DEBUG
docker logs mock-bank-service 2>&1 | grep -iE "ERROR|Exception"
```

## Check why a container is unhealthy

```bash
docker inspect --format "{{json .State.Health}}" payment-service
```

Shows the last few healthcheck attempts and their output — useful when `docker compose ps` just says `(unhealthy)` with no detail.

## Check a service's Actuator health directly (from the host)

```bash
curl http://localhost:8080/actuator/health   # payment-service
curl http://localhost:8081/actuator/health   # mock-bank-service
curl http://localhost:8082/actuator/health   # merchant-service
curl http://localhost:8083/actuator/health   # webhook-service
curl http://localhost:8084/actuator/health   # settlement-service
curl http://localhost:9000/actuator/health   # gateway-service
```

Every service also exposes `/actuator/info` and `/actuator/metrics` (see `DEPLOYMENT.md` — `/actuator/prometheus` is exposed but won't return real Prometheus-format output until the `micrometer-registry-prometheus` dependency is added in a later phase).

## Rebuild after a code change

```bash
docker compose up -d --build payment-service
```

`docker compose restart` alone does **not** pick up code changes — it just restarts the existing container/image. Use `up -d --build` for that service whenever you've edited its source.

---

# 3. PostgreSQL Commands

Four independent Postgres 16 instances — one per data-owning service (mock-bank-service and gateway-service have none):

| Container | Database | User | Host port |
|---|---|---|---|
| `payment_postgres` | `payment_db` | `payment_user` | 5432 |
| `merchant_postgres` | `merchant_db` | `merchant_user` | 5433 |
| `webhook_postgres` | `webhook_db` | `webhook_user` | 5434 |
| `settlement_postgres` | `settlement_db` | `settlement_user` | 5435 |

## Open a PostgreSQL shell

```bash
docker exec -it payment_postgres psql -U payment_user -d payment_db
docker exec -it merchant_postgres psql -U merchant_user -d merchant_db
docker exec -it webhook_postgres psql -U webhook_user -d webhook_db
docker exec -it settlement_postgres psql -U settlement_user -d settlement_db
```

---

## List databases

Inside psql:

```sql
\l
```

---

## Connect to database

```sql
\c payment_db
```

---

## List tables

```sql
\dt
```

---

## Describe table structure

Example:

```sql
\d payments
```

Shows:

* columns
* types
* constraints
* indexes

---

## View all rows

```sql
SELECT * FROM payments;
```

---

## Limit rows

```sql
SELECT * FROM payments LIMIT 10;
```

---

## Exit PostgreSQL shell

```sql
\q
```

---

## Delete all rows

⚠️ Dangerous

```sql
DELETE FROM payments;
```

---

## Drop table

⚠️ Dangerous

```sql
DROP TABLE payments;
```

---

# 4. Redis Commands

Container:

```text
payment_redis
```

Only `payment-service` uses Redis (cache-aside on payment lookups) — no other service touches it.

---

## Open Redis CLI

```bash
docker exec -it payment_redis redis-cli
```

---

## Check Redis health

```redis
PING
```

Expected:

```text
PONG
```

---

## Set value

```redis
SET user:1 "john"
```

---

## Get value

```redis
GET user:1
```

---

## List all keys

```redis
KEYS *
```

---

## Delete key

```redis
DEL user:1
```

---

## Check TTL

```redis
TTL user:1
```

---

## Exit Redis CLI

```redis
exit
```

---

# 5. Kafka Commands

Container:

```text
payment_kafka
```

**Dual listeners** (added in the Stage 4 refactor): `PLAINTEXT` on `kafka:29092` for container-to-container traffic (what all six app services use), `PLAINTEXT_HOST` on `localhost:9092` for host-machine access. Commands below run `docker exec` *inside* the Kafka container itself, so `localhost:9092` is correct for all of them — you're not crossing the container boundary.

---

## List topics

```bash
docker exec payment_kafka kafka-topics \
--list \
--bootstrap-server localhost:9092
```

`payment.initiated`, `payment.processed`, `payment.succeeded` (plus their `.DLT` variants) should already exist — they're created automatically by the one-shot `kafka-topic-init` container on first `docker compose up`, since `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`.

---

## Create topic

```bash
docker exec payment_kafka kafka-topics \
--create \
--topic payment.initiated \
--bootstrap-server localhost:9092 \
--partitions 1 \
--replication-factor 1
```

You normally shouldn't need this manually — see "Re-run topic provisioning" below.

---

## Describe topic

```bash
docker exec payment_kafka kafka-topics \
--describe \
--topic payment.initiated \
--bootstrap-server localhost:9092
```

---

## Delete topic

⚠️ Dangerous

```bash
docker exec payment_kafka kafka-topics \
--delete \
--topic payment.initiated \
--bootstrap-server localhost:9092
```

---

## Produce messages manually

```bash
docker exec -it payment_kafka kafka-console-producer \
--topic payment.initiated \
--bootstrap-server localhost:9092
```

Type messages:

```text
hello
payment-created
test-event
```

Press Enter after each.

---

## Consume messages

```bash
docker exec -it payment_kafka kafka-console-consumer \
--topic payment.initiated \
--from-beginning \
--bootstrap-server localhost:9092
```

---

## Consume only NEW messages

```bash
docker exec -it payment_kafka kafka-console-consumer \
--topic payment.initiated \
--bootstrap-server localhost:9092
```

---

## Check topic provisioning logs

```bash
docker logs kafka_topic_init
```

This container runs once (`scripts/provision_kafka_topics.py`) and exits — `docker compose ps` will show it as `Exited (0)`, which is expected, not a failure.

---

## Re-run topic provisioning manually

If topics somehow didn't get created (e.g. you ran `docker compose down -v` and only brought Kafka back up):

```bash
docker compose up kafka-topic-init
```

Or standalone from the host, without Docker at all:

```bash
pip install -r scripts/requirements.txt
python scripts/provision_kafka_topics.py --bootstrap-servers localhost:9092
```

Idempotent — safe to re-run, it only creates topics that don't already exist. The same script works against AWS MSK later by pointing `--bootstrap-servers` at the MSK broker list.

---

# 6. Common Debugging Commands

## Check overall system health at a glance

```bash
docker compose ps --format "table {{.Name}}\t{{.Status}}"
```

## Check Kafka startup logs

```bash
docker logs payment_kafka
```

---

## Check Postgres startup logs

```bash
docker logs payment_postgres
docker logs merchant_postgres
docker logs webhook_postgres
docker logs settlement_postgres
```

---

## Check Redis startup logs

```bash
docker logs payment_redis
```

---

## A service is "Up" but not "healthy" — now what

1. `docker inspect --format "{{json .State.Health}}" <container>` — see the actual healthcheck output/error.
2. `docker logs <container>` — look for a stack trace during startup (most common: DB not ready yet, or a bad env var).
3. `docker exec -it <container> sh` then `curl -s localhost:<port>/actuator/health` — call the healthcheck by hand to see the raw response Spring Boot is giving.

## A service can't reach another service

Check the env var it's using to reach it (e.g. `MERCHANT_SERVICE_URL`) actually points at a **Docker service name**, not `localhost` — see the beginner mistake section below. Confirm with:

```bash
docker exec -it payment-service sh -c "env | grep _URL"
```

---

# 7. Useful Mental Models

## Docker

```text
Image -> Blueprint
Container -> Running app
Volume -> Persistent storage
```

---

## Kafka

```text
Producer -> Topic -> Consumer
```

Kafka stores messages in topics.

Consumers track offsets.

---

## Redis

```text
Redis = in-memory key-value store
```

Mostly used for:

* caching
* sessions
* rate limiting
* temporary state

---

## PostgreSQL

```text
Postgres = persistent relational database
```

Stores:

* users
* payments
* transactions
* application state

---

# 8. MOST COMMON Beginner Mistake

Inside Docker:

```text
localhost != other container
```

Containers communicate using service names:

```text
postgres
merchant-db
webhook-db
settlement-db
redis
kafka
payment-service
merchant-service
```

Example:

```yaml
spring.datasource.url=jdbc:postgresql://postgres:5432/payment_db
```

NOT:

```yaml
localhost
```

when Spring Boot also runs inside Docker. This is exactly why Kafka runs **two** listeners (`kafka:29092` for containers, `localhost:9092` for the host) rather than one — see the Kafka section above.

---

# 9. See Also

* [`DEPLOYMENT.md`](DEPLOYMENT.md) — full environment-variable reference, startup sequence, Spring profiles.
* [`postman/Distributed-Payment-Gateway.postman_collection.json`](postman/Distributed-Payment-Gateway.postman_collection.json) — importable Postman collection exercising every service.
* [`CHANGELOG.md`](CHANGELOG.md) — what changed in the Stage 4 infra/config refactor.
