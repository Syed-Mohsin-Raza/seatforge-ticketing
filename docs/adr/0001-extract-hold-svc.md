# ADR-0001: Extract the seat hold primitive into a Go gRPC service

- **Status:** Accepted
- **Date:** 2026-10-03

## Context

SeatForge's core correctness guarantee is that no seat can be confirmed twice.
The original implementation placed the hold logic inside `booking-api`:
`SeatReservationService.holdSeat` opened a JPA transaction, took a
`PESSIMISTIC_WRITE` lock on the seat row, and inserted a `HELD` booking.

This worked. The concurrency test (10 threads racing for one seat, exactly
one winner) passed.

However, one goal of the project is to demonstrate a real cross-language
service boundary, and the hold primitive is the natural candidate: it is
small, concurrency-critical, and has a clear contract. Keeping it in-process
would not serve that goal.

## Decision

Extract the seat reservation primitive into a separate Go service exposing a
narrow gRPC contract:

```proto
rpc ReserveSeat(seat_id, user_id, hold_duration_seconds)
    returns (booking_id, status, expires_at);
rpc ReleaseSeat(booking_id)
    returns (released);
```

- **Narrow scope.** hold-svc owns only the concurrency-critical primitive.
  Confirm and cancel transitions remain in booking-api.
- **Shared PostgreSQL.** hold-svc connects to the same database as booking-api.
  No schema fork, no dual-write.
- **Migrations stay in booking-api.** hold-svc reads the schema and does not
  modify it.
- **No `event_id` in the request.** The server derives it from the seat row,
  which removes a lazy-load dependency on the Java side.

## Consequences

### Positive

- The concurrency-critical code path is small, isolated, and easy to reason
  about. It has its own test suite, run with `go test -race`.
- The race detector adds a check for in-process data races in the Go code.
  Database-level correctness still comes from the row lock and the partial
  unique indexes, not from the detector.
- The gRPC boundary is a real service boundary, not a synthetic one.
- Services can be deployed and scaled separately. Holds are the hot path;
  confirm and cancel are not. Overall throughput remains bounded by the
  shared PostgreSQL instance.

### Negative

- Every hold request now has an extra network hop. The planned k6 load test
  will quantify the cost.
- Two deployable artifacts instead of one: more CI and more build pipelines.
- The proto contract is duplicated between `booking-api` and `hold-svc`.
- The two services are coupled through the database schema. A migration in
  booking-api can break hold-svc without any compile-time signal.
- The Java integration tests can no longer exercise the hold flow without a
  running hold-svc. Three tests are disabled until an in-process gRPC fake
  exists (planned for ADR-0002).

### Neutral

- PostgreSQL remains the single source of truth. No distributed transaction
  is introduced. The correctness mechanism is unchanged; only the process
  boundary moves.

## Alternatives considered

1. **Keep holds in booking-api.** Simplest option and it works, but it gives
   up the cross-language boundary the project sets out to demonstrate.
2. **Extract the whole booking lifecycle to hold-svc.** Every confirm and
   cancel request would cross the gRPC boundary, adding latency to operations
   that do not need serialization. Rejected.
3. **Separate databases per service, coordinated with a SAGA or 2PC.**
   Unnecessary while a single database can enforce the invariants with one
   local transaction. Rejected as premature complexity.

## Follow-up work

- Replace the three disabled Java integration tests with an in-process gRPC
  fake (ADR-0002).
- Move the proto contract into a shared repository or Maven artifact.
- Add mTLS and service authentication before any real deployment.
- Measure the latency of the gRPC hop under load with k6.
