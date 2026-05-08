# Docker + Postgres + Redis + Kafka Cheat Sheet

*For Spring Boot Microservice Development*

---

# 1. General Docker Commands

## Start all services

```bash
docker compose up -d
```

---

## Stop all services

```bash
docker compose down
```

---

## Stop + DELETE all volumes/data

⚠️ Dangerous. Deletes Postgres DB + Kafka topics + Redis data.

```bash
docker compose down -v
```

---

## Check running containers

```bash
docker ps
```

---

## Check all containers (including stopped)

```bash
docker ps -a
```

---

## View logs

```bash
docker logs payment_postgres
docker logs payment_redis
docker logs payment_kafka
docker logs payment_zookeeper
```

---

## Follow logs live

```bash
docker logs -f payment_kafka
```

---

## Enter container shell

```bash
docker exec -it payment_postgres bash
docker exec -it payment_redis sh
docker exec -it payment_kafka bash
```

---

# 2. PostgreSQL Commands

Container:

```text
payment_postgres
```

Database:

```text
payment_db
```

User:

```text
payment_user
```

---

## Open PostgreSQL shell

```bash
docker exec -it payment_postgres psql -U payment_user -d payment_db
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

# 3. Redis Commands

Container:

```text
payment_redis
```

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

# 4. Kafka Commands

Container:

```text
payment_kafka
```

---

## List topics

```bash
docker exec payment_kafka kafka-topics \
--list \
--bootstrap-server localhost:9092
```

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

# 5. Common Debugging Commands

## Check service health

```bash
docker ps
```

---

## Check Kafka startup logs

```bash
docker logs payment_kafka
```

---

## Check Postgres startup logs

```bash
docker logs payment_postgres
```

---

## Check Redis startup logs

```bash
docker logs payment_redis
```

---

# 6. Useful Mental Models

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

# 7. MOST COMMON Beginner Mistake

Inside Docker:

```text
localhost != other container
```

Containers communicate using service names:

```text
postgres
redis
kafka
```

Example:

```yaml
spring.datasource.url=jdbc:postgresql://postgres:5432/payment_db
```

NOT:

```yaml
localhost
```

when Spring Boot also runs inside Docker.

---
