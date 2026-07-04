# Distributed Payment Gateway

> A production-grade distributed payment gateway built with Java 21 & Spring Boot.
> Covers microservices, async Kafka processing, resilience patterns, merchant webhooks, and end-of-day settlement.

## Architecture

> Architecture diagram will be added upon project completion.

## Services

| Service | Port | Description | Status |
|---|---|---|---|
| payment-service | 8080 | Core payment processing | 🟢 Completed|
| mock-bank-service | 8081 | Simulates bank processing | 🟢 Completed |
| merchant-service | 8082 | Merchant registration & API keys | 🟢 Completed |
| webhook-service | 8083 | Merchant webhook notifications | 🟢 Completed |
| settlement-service | 8084 | End-of-day batch settlement | 🟢 Completed |
| auth-service | TBD | Centralized authentication | ⬜ Planned |
| api-gateway | 9000 | Single entry point | 🟡 In Progress |

## Tech Stack

| Category | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4 |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Messaging | Apache Kafka |
| Resilience | Resilience4j |
| Batch | Spring Batch |
| Gateway | Spring Cloud Gateway |
| Tracing | Micrometer + Zipkin |
| Metrics | Prometheus + Grafana |
| Migrations | Flyway |
| Containers | Docker + Docker Compose |
| Build | Gradle |
| API Testing | Bruno |

## Getting Started

### Prerequisites
- Java 21
- Docker & Docker Compose
- Bruno (API testing)

### Run everything with Docker Compose

Every service now has its own multi-stage `Dockerfile` and is wired into the root `docker-compose.yml`, alongside the infra containers (Postgres ×4, Redis, Zookeeper, Kafka) and a one-shot `kafka-topic-init` container that provisions the required Kafka topics (`KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`, so nothing else does this automatically — see `scripts/provision_kafka_topics.py`).

```bash
git clone https://github.com/yourusername/distributed-payment-gateway.git
cd distributed-payment-gateway

docker compose up -d --build
```

This builds and starts all six services plus infra, in the right order (`depends_on` + healthchecks — app services wait for their DB/Kafka/topic-init to actually be ready, not just "container started"). Ports are unchanged from the table above and remain reachable from the host. See [DEPLOYMENT.md](DEPLOYMENT.md) for the full environment-variable reference, the dev/sit/prod Spring profile setup, and notes on what's still needed before an AWS deployment.

### Run a service locally without Docker

```bash
# Copy and fill in environment variables (every service now has its own .env.example)
cp payment-service/.env.example payment-service/.env
# ...repeat for each service you want to run locally

# Start infrastructure only
docker compose up -d postgres merchant-db webhook-db settlement-db redis zookeeper kafka kafka-topic-init

# Then run the service via IntelliJ/your IDE, or:
cd service-name
./gradlew bootRun
```

### API Endpoints

> Full API documentation via Swagger will be available at `/swagger-ui.html` on each service upon project completion.

A Postman collection covering every service (health checks, merchant registration, payments across all three methods, webhook admin, and a note on settlement-service's still-missing REST API) lives at [`postman/Distributed-Payment-Gateway.postman_collection.json`](postman/Distributed-Payment-Gateway.postman_collection.json) — import it directly, it's self-contained (default variables point at the standard local/Docker Compose ports). Verified end-to-end against a live `docker compose up` stack via Newman on 2026-07-04.

TABLE WILL BE ADDED DURING PHASE 7 IMPLEMENTATION

## Project Progress

See [PROGRESS.md](PROGRESS.md) for detailed build status and key decisions made at each phase.


