# Distributed Payment Gateway

> A production-grade distributed payment gateway built with Java 21 & Spring Boot.
> Covers microservices, async Kafka processing, resilience patterns, merchant webhooks, and end-of-day settlement.

## Architecture

> Architecture diagram will be added upon project completion.

## Services

| Service | Port | Description | Status |
|---|---|---|---|
| payment-service | 8080 | Core payment processing | 🟢 Completed|
| mock-bank-service | 8081 | Simulates bank processing | 🟡 In Progress |
| merchant-service | TBD | Merchant registration & API keys | ⬜ Planned |
| webhook-service | TBD | Merchant webhook notifications | ⬜ Planned |
| settlement-service | TBD | End-of-day batch settlement | ⬜ Planned |
| auth-service | TBD | Centralized authentication | ⬜ Planned |
| api-gateway | TBD | Single entry point | ⬜ Planned |

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

### Run Locally

```bash
# Clone the repo
git clone https://github.com/yourusername/distributed-payment-gateway.git
cd distributed-payment-gateway

# Copy and fill in environment variables
cp payment-service/.env.example payment-service/.env

# Start infrastructure
docker compose up -d

# Start Payment Service
cd payment-service
./gradlew bootRun

# Start Mock Bank Service (separate terminal)
cd mock-bank-service
./gradlew bootRun
```

### API Endpoints

> Full API documentation via Swagger will be available at `/swagger-ui.html` on each service upon project completion.

| Method | Endpoint | Service | Description |
|---|---|---|---|
| POST | `/auth/token` | payment-service | Get JWT token |
| POST | `/api/v1/payments` | payment-service | Create payment |
| GET | `/api/v1/payments/{id}` | payment-service | Get payment by ID |
| POST | `/bank/process` | mock-bank-service | Process payment (internal) |

## Project Progress

See [PROGRESS.md](PROGRESS.md) for detailed build status and key decisions made at each phase.


