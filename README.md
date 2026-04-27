# Checkout System

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.14-brightgreen?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![AWS SQS](https://img.shields.io/badge/AWS%20SQS-LocalStack-FF9900?logo=amazonsqs&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

A production-grade payment checkout service demonstrating idempotent request handling, a strict payment state machine, async event processing via SQS, distributed caching with Redis, and full observability through Prometheus and Grafana.

---

## Architecture

```
                         POST /api/v1/checkout
                         Idempotency-Key: <uuid>
┌──────────┐                                        ┌──────────────────────────────┐
│  Client  │ ──────────────────────────────────►   │     Checkout Service          │
└──────────┘                                        │     Spring Boot :8080         │
                                                     └───┬──────────┬───────────────┘
                                                         │          │
                                          ┌──────────────┘          └─────────────────┐
                                          │                                            │
                                          ▼                                            ▼
                               ┌──────────────────┐                       ┌──────────────────┐
                               │    PostgreSQL     │                       │      Redis        │
                               │  payments table   │                       │  idempotency      │
                               │  (port 5432)      │                       │  cache (port 6379)│
                               └──────────────────┘                       └──────────────────┘
                                                                                    │
                                                          ┌─────────────────────────┘
                                                          ▼
                                               ┌──────────────────┐
                                               │    AWS SQS        │
                                               │  payment-events   │
                                               │ (LocalStack 4566) │
                                               └────────┬─────────┘
                                                        │ poll every 5s
                                        ┌───────────────┼───────────────┐
                                        ▼               ▼               ▼
                                 ┌────────────┐  ┌────────────┐  ┌────────────┐
                                 │  Receipt   │  │   Fraud    │  │ Inventory  │
                                 │  Consumer  │  │  Consumer  │  │  Consumer  │
                                 └────────────┘  └────────────┘  └────────────┘
                                                        │
                                                        ▼
                                               ┌──────────────────┐
                                               │   Capture Job    │
                                               │  every 30 seconds│
                                               │ AUTHORIZED       │
                                               │   → CAPTURED     │
                                               │     → SETTLED    │
                                               └──────────────────┘
```

---

## Features

- **Idempotency** — `Idempotency-Key` header backed by Redis (24-hour TTL) prevents duplicate charges on retries
- **Payment state machine** — strict transition enforcement with optimistic locking (`@Version`) to prevent concurrent state corruption
- **Async SQS events** — authorized payments publish to a `payment-events` queue; a scheduled consumer polls and fans out to downstream processors
- **Distributed caching** — Redis stores idempotency responses so repeated requests are served without hitting the database
- **Scheduled capture** — `CaptureJob` runs every 30 seconds to batch-advance `AUTHORIZED` payments through `CAPTURED` to `SETTLED`
- **Observability** — Micrometer counters exposed via `/actuator/prometheus`, scraped by Prometheus, visualised in Grafana; distributed tracing via OpenTelemetry + Zipkin

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.5.14 |
| Persistence | PostgreSQL 15, Spring Data JPA / Hibernate |
| Caching | Redis 7, Spring Data Redis |
| Messaging | AWS SQS (LocalStack 3), AWS SDK v2 |
| Metrics | Micrometer, Prometheus, Grafana |
| Tracing | OpenTelemetry, Zipkin exporter |
| Build | Maven 3, Lombok |
| Infrastructure | Docker Compose |

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java (JDK) | 17 or later (21 recommended) |
| Docker & Docker Compose | Any recent stable version |
| Maven | 3.6+ (or use the included `./mvnw` wrapper) |

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-org/checkout-system.git
cd checkout-system
```

### 2. Start infrastructure services

```bash
docker compose up -d
```

This starts PostgreSQL, Redis, LocalStack (SQS), Prometheus, and Grafana. LocalStack automatically creates the `payment-events` queue via `localstack-init/01-create-queues.sh`.

Wait a few seconds for LocalStack to initialise before starting the application.

### 3. Run the application

```bash
./mvnw spring-boot:run
```

The timezone flag (`-Duser.timezone=UTC`) is pre-configured in `.mvn/jvm.config` and applied automatically. To override explicitly:

```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Duser.timezone=UTC"
```

The service starts on **http://localhost:8080**.

---

## API Documentation

### POST /api/v1/checkout

Initiates a checkout and returns an authorized payment. Supply a unique `Idempotency-Key` header; retrying with the same key returns the cached response without creating a duplicate charge.

**Request**

```bash
curl -X POST http://localhost:8080/api/v1/checkout \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "orderId":    "order-001",
    "customerId": "customer-123",
    "amount":     99.99,
    "currency":   "USD"
  }'
```

**Response** `201 Created`

```json
{
  "paymentId":  "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "orderId":    "order-001",
  "status":     "AUTHORIZED",
  "amount":     99.99,
  "currency":   "USD",
  "createdAt":  "2026-04-27T10:00:00"
}
```

| Field | Type | Description |
|---|---|---|
| `orderId` | string | Unique order identifier |
| `customerId` | string | Customer identifier |
| `amount` | decimal | Payment amount (up to 4 decimal places) |
| `currency` | string | ISO 4217 currency code (e.g. `USD`) |

**Error responses**

| Status | Reason |
|---|---|
| `409 Conflict` | `orderId` already has a payment |
| `400 Bad Request` | Missing or invalid request fields |

---

### GET /api/v1/payments

Returns all payments.

```bash
curl http://localhost:8080/api/v1/payments
```

**Response** `200 OK`

```json
[
  {
    "paymentId":  "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "orderId":    "order-001",
    "status":     "SETTLED",
    "amount":     99.99,
    "currency":   "USD",
    "createdAt":  "2026-04-27T10:00:00"
  }
]
```

---

### GET /api/v1/payments/{id}

Returns a single payment by its UUID.

```bash
curl http://localhost:8080/api/v1/payments/3fa85f64-5717-4562-b3fc-2c963f66afa6
```

**Response** `200 OK` — same shape as a single element from the list above.  
**Response** `404 Not Found` — payment ID does not exist.

---

## Payment State Machine

```
              ┌───────────┐
              │  PENDING  │◄── initial state on checkout
              └─────┬─────┘
                    │
          ┌─────────┴──────────┐
          ▼                    ▼
   ┌────────────┐         ┌────────┐
   │ AUTHORIZED │         │ FAILED │ (terminal)
   └─────┬──────┘         └────────┘
         │         ╲
         │          └──────────────► FAILED (terminal)
         ▼
   ┌──────────┐
   │ CAPTURED │◄── CaptureJob (every 30s)
   └─────┬────┘
         │
         ▼
   ┌──────────┐
   │ SETTLED  │◄── CaptureJob (same run)
   └─────┬────┘
         │
         ▼
   ┌──────────┐
   │ REFUNDED │ (terminal)
   └──────────┘
```

Illegal transitions throw `InvalidStateTransitionException` (HTTP 409). Optimistic locking (`@Version`) on the `payments` table prevents concurrent state corruption.

---

## Observability

### Prometheus — http://localhost:9090

Prometheus scrapes `/actuator/prometheus` on the application every 15 seconds. Useful queries:

```promql
# Total checkout attempts
payment_requests_total

# Authorization success rate
rate(payment_success_total[5m]) / rate(payment_requests_total[5m])

# Settled payments per minute
rate(payment_settled_total[1m]) * 60
```

Custom counters exposed by the service:

| Metric | Description |
|---|---|
| `payment_requests_total` | Every checkout attempt |
| `payment_success_total` | Successful authorizations |
| `payment_failure_total` | Failed payments |
| `payment_settled_total` | Payments settled by `CaptureJob` |

The raw metrics endpoint is also available directly at:

```bash
curl http://localhost:8080/actuator/prometheus
```

### Grafana — http://localhost:3000

Login with `admin` / `admin`.

1. Add a Prometheus data source pointing to `http://prometheus:9090`
2. Import or build a dashboard using the metrics above

### Health & Info

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/info
curl http://localhost:8080/actuator/metrics
```

### Distributed Tracing

All requests are traced at 100% sample rate (`management.tracing.sampling.probability: 1.0`) and exported in Zipkin format. Run a Zipkin instance on port 9411 to collect traces.

---

## Environment Variables

The defaults below match the `docker-compose.yml` and `application.yaml` configuration for local development.

| Variable | Default | Description |
|---|---|---|
| `POSTGRES_DB` | `checkoutdb` | PostgreSQL database name |
| `POSTGRES_USER` | `checkout_user` | PostgreSQL username |
| `POSTGRES_PASSWORD` | `checkout_pass` | PostgreSQL password |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/checkoutdb` | Full JDBC URL |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis hostname |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `AWS_REGION` | `us-east-1` | AWS region for SQS |
| `AWS_SQS_ENDPOINT` | `http://localhost:4566` | SQS endpoint (LocalStack) |
| `AWS_SQS_QUEUE_URL` | `http://localhost:4566/000000000000/payment-events` | Full SQS queue URL |
| `GF_SECURITY_ADMIN_PASSWORD` | `admin` | Grafana admin password |
| `TZ` / `user.timezone` | `UTC` | JVM and DB timezone (must match) |

---

## Project Structure

```
checkout-system/
├── .mvn/
│   ├── jvm.config                          # -Duser.timezone=UTC applied to all Maven runs
│   └── wrapper/
├── localstack-init/
│   └── 01-create-queues.sh                 # Auto-creates payment-events queue on LocalStack start
├── src/
│   └── main/
│       ├── java/com/checkout/checkout_system/
│       │   ├── config/
│       │   │   ├── MetricsConfig.java       # Micrometer counter beans
│       │   │   ├── RedisConfig.java         # RedisTemplate configuration
│       │   │   └── SqsConfig.java           # SqsAsyncClient + queue URL beans
│       │   ├── consumer/
│       │   │   └── PaymentEventConsumer.java # Polls SQS every 5s, fans out to processors
│       │   ├── controller/
│       │   │   ├── CheckoutController.java   # POST /api/v1/checkout
│       │   │   └── PaymentController.java    # GET /api/v1/payments[/{id}]
│       │   ├── dto/
│       │   │   ├── CheckoutRequest.java
│       │   │   └── CheckoutResponse.java
│       │   ├── enums/
│       │   │   └── PaymentStatus.java        # PENDING, AUTHORIZED, CAPTURED, SETTLED, FAILED, REFUNDED
│       │   ├── exception/
│       │   │   ├── DuplicatePaymentException.java
│       │   │   ├── GlobalExceptionHandler.java
│       │   │   └── InvalidStateTransitionException.java
│       │   ├── job/
│       │   │   └── CaptureJob.java           # Scheduled: AUTHORIZED → CAPTURED → SETTLED every 30s
│       │   ├── model/
│       │   │   └── Payment.java              # JPA entity with @Version for optimistic locking
│       │   ├── repository/
│       │   │   └── PaymentRepository.java
│       │   ├── service/
│       │   │   ├── CheckoutService.java      # Orchestrates idempotency, persistence, auth, SQS publish
│       │   │   ├── IdempotencyService.java   # Redis-backed key check with 24h TTL
│       │   │   ├── PaymentService.java       # Query helpers for PaymentController
│       │   │   └── PaymentStateService.java  # Enforces allowed state transitions
│       │   └── CheckoutSystemApplication.java
│       └── resources/
│           └── application.yaml
├── docker-compose.yml                        # PostgreSQL, Redis, LocalStack, Prometheus, Grafana
├── prometheus.yml                            # Scrape config targeting host.docker.internal:8080
└── pom.xml                                   # Spring Boot 3.5.14, AWS SDK v2, Micrometer, OTel
```
