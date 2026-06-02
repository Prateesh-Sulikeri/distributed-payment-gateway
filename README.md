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

### Run Locally

```bash
# Clone the repo
git clone https://github.com/yourusername/distributed-payment-gateway.git
cd distributed-payment-gateway

# Copy and fill in environment variables
cp payment-service/.env.example payment-service/.env

# Start infrastructure
docker compose up -d

# Start the individual services via intelliJ / IDE of your choice  or use 
cd service-name
./gradlew bootRun
```

### API Endpoints

> Full API documentation via Swagger will be available at `/swagger-ui.html` on each service upon project completion.

TABLE WILL BE ADDED DURING PHASE 7 IMPLEMENTATION

## Project Progress

See [PROGRESS.md](PROGRESS.md) for detailed build status and key decisions made at each phase.


