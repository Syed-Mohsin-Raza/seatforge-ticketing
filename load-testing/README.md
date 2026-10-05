# Load Testing

k6 scenarios for measuring SeatForge performance.

## Prerequisites

- k6 v2.0+
- Docker Compose stack running: `docker compose up -d postgres redis`
- hold-svc running on `:9090`
- booking-api running on `:8080` with the `dev` profile

## Running

Ensure the seed has enough seats:

```bash
docker exec -it seatforge-postgres psql -U seatforge -d seatforge -c \
  "SELECT COUNT(*) FROM seats;"