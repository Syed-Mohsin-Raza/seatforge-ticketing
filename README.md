# SeatForge

A distributed ticketing system that guarantees a seat is never booked twice, built as a polyglot reference implementation with Java, Go, PostgreSQL, and Redis.

## Overview

SeatForge sells seats for events. A user can place a hold on a seat, confirm the booking, or cancel it. Correctness is enforced at the database layer, so the guarantee that a seat has at most one confirmed booking holds regardless of how many clients compete for it.

The project demonstrates:

- Double-booking prevention using row-level locks and partial unique indexes
- Cross-language service boundaries over gRPC
- A cache that improves read performance without ever being relied on for correctness

## Architecture

```text
                    ┌─────────────────────────┐
                    │         Client          │
                    └────────────┬────────────┘
                                 │ HTTP / REST
                                 ▼
                    ┌─────────────────────────┐
                    │  booking-api            │
                    │  Java 25 / Spring Boot  │
                    │  Port 8080              │
                    └──┬─────────┬─────────┬──┘
                       │         │         │
                  gRPC │    JDBC │   cache │
                       ▼         │         ▼
               ┌───────────────┐ │  ┌───────────────┐
               │  hold-svc     │ │  │  Redis        │
               │  Go, port 9090│ │  │  read cache   │
               └───────┬───────┘ │  └───────────────┘
                  JDBC │         │
                       ▼         ▼
               ┌─────────────────────────────┐
               │         PostgreSQL          │
               │       source of truth       │
               └─────────────────────────────┘
```

booking-api and hold-svc share one PostgreSQL database. This is intentional: it keeps a single transactional authority for booking state and avoids distributed transactions on the concurrency-critical path.

### Components

| Component | Technology | Responsibility |
|---|---|---|
| booking-api | Java 25, Spring Boot 4.1 | REST entry point. Owns event, seat, booking, and cache endpoints, plus the expiry sweeper for stale holds. |
| hold-svc | Go, gRPC | Owns the seat reservation primitive (`ReserveSeat`, `ReleaseSeat`). gRPC only, no HTTP. |
| PostgreSQL | PostgreSQL | Source of truth for all bookings. Enforces correctness through locks and constraints. |
| Redis | Redis | Read-through cache for seat availability. Optimization only. |

See [`docs/architecture.md`](docs/architecture.md) for the full design and [`docs/adr/`](docs/adr/) for decision records.

## Correctness Invariants

1. A seat has at most one `CONFIRMED` booking.
2. A seat has at most one active `HELD` booking.
3. No LLM or external agent may mutate booking, inventory, or payment state.
4. Redis is never the sole correctness mechanism.

Enforcement:

- Partial unique indexes on `bookings(seat_id)` for `HELD` and `CONFIRMED` statuses
- `SELECT ... FOR UPDATE` row locks inside the hold transaction
- An optimistic `@Version` field on the `Booking` entity as a defensive layer

## Getting Started

### Prerequisites

- Java 25
- Maven 3.6.3+ (the project ships `mvnw`)
- Go 1.22+
- Docker and Docker Compose
- `protoc`, `protoc-gen-go`, and `protoc-gen-go-grpc`

### Run locally

1. Start the dependencies:

   ```bash
   docker compose up -d postgres redis
   ```

2. Start the Go hold service:

   ```bash
   cd backend/hold-svc
   export DATABASE_URL="postgres://seatforge:seatforge@localhost:5432/seatforge?sslmode=disable"
   export GRPC_PORT=9090
   make build
   ./bin/hold-svc
   ```

3. Start the Java booking API:

   ```bash
   cd backend/booking-api
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   Under the `dev` profile, Flyway applies migrations and seeds 1000 seats on startup.

### Smoke test

```bash
curl -X POST http://localhost:8080/api/v1/bookings/holds \
  -H 'Content-Type: application/json' \
  -d '{"seatId":100,"userId":"demo-user"}'
```

A second hold request for the same seat returns `409 Conflict`.

## Testing

Go (with the race detector):

```bash
cd backend/hold-svc
export DATABASE_URL="postgres://seatforge:seatforge@localhost:5432/seatforge?sslmode=disable"
go test -race ./...
```

Java:

```bash
cd backend/booking-api
./mvnw clean verify
```

Three Java integration tests covering the gRPC hop are currently `@Disabled` pending an in-process gRPC fake (see [ADR-0002](docs/adr/0002)). The end-to-end flow is verified manually with `curl` and `psql`.

### Load testing

See [`load-testing/README.md`](load-testing/README.md). The full stack must be running first: PostgreSQL, Redis, hold-svc, and booking-api. The scenario ramps hold requests from 0 to 700 virtual users (VUs) and runs a cached read baseline at 500 fixed VUs. Results are written to `load-testing/results/`, which is git-ignored.

## Repository Layout

```text
seatforge-ticketing/
├── backend/
│   ├── booking-api/        Java, Spring Boot 4.1
│   ├── hold-svc/           Go, gRPC
│   ├── payment-svc/        planned
│   └── notification-svc/   planned
├── frontend/               planned
├── agent/                  planned
├── infra/cdk/              planned
├── load-testing/           k6 scenarios
├── docs/
│   ├── architecture.md
│   └── adr/
├── docker-compose.yml
└── README.md
```

## Roadmap

- [x] booking-api with concurrency-safe holds (PostgreSQL row locking)
- [x] Redis read-through cache with explicit invalidation
- [x] hold-svc in Go with gRPC; booking-api delegates holds
- [ ] Load test the gRPC architecture with k6
- [ ] payment-svc (Java) with idempotency keys
- [ ] notification-svc (Go) consuming SQS
- [ ] AWS CDK stack: ECS Fargate, ALB, RDS, ElastiCache
- [ ] OpenTelemetry instrumentation across services

## Scope

### Non-goals

- This is a reference implementation, not a production service.
- Real payment processing and a polished end-user frontend are out of scope.
- Multi-region deployment and cross-database transactions are out of scope. Booking state lives in a single PostgreSQL instance by design.

### Known limitations

These are gaps to be closed, not design choices:

- **No authentication or authorization.** Any caller can place or cancel holds.
- **No transport security.** Service-to-service gRPC is plaintext; there is no TLS.
- **Static service address.** `HoldServiceClient` uses a fixed hold-svc address; there is no service discovery.
- **No distributed tracing.** Instrumentation is planned.
- **Disabled integration tests.** See the Testing section.
- **Duplicated proto contract.** The definition exists in both `booking-api` and `hold-svc`; a shared proto repository is planned.
- **Stub services.** `payment-svc` and `notification-svc` do not exist yet.