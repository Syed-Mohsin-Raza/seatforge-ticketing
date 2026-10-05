# ADR-0002: Size the hold-svc connection pool from local load tests

- **Status:** Accepted
- **Date:** 2026-10-05

## Context

The hold primitive was extracted from booking-api into a Go gRPC service
(ADR-0001). The expected cost is one extra network hop per hold request.
A series of load tests was run locally to quantify the cost and detect
failure modes. All components, including the load generators, ran on one
machine.

## Decision

Set hold-svc's `SetMaxOpenConns` to 100 for local development. Treat the
other local numbers as indicative, not as capacity figures, and re-measure
in a deployed environment before making any production sizing or capacity
claim.

## Measurements

### hold-svc in isolation

| Tool | Concurrency | Workload | Avg latency |
|---|---|---|---|
| grpcurl sequential | 1 | 100 distinct seats | 25 ms |
| ghz (AlreadyExists path) | 100 | Same seat, all conflicts | 60 ms |

### Connection pool experiment

`SetMaxOpenConns` in hold-svc was raised from 20 to 100. Workload: custom
Go benchmark, 100 concurrent workers, 10,000 distinct seats.

| Pool size | Avg latency | Throughput |
|---|---|---|
| 20 | 280 ms | 354 req/s |
| 100 | 200 ms | 496 req/s |

Both runs started from a clean database state (all bookings deleted) and
produced zero failures.

### Full stack through booking-api (k6)

| VUs | hold_success | hold_latency med | hold_latency p(95) |
|---|---|---|---|
| 100 | 7,015 | 533 ms | 1.05 s |
| 700 | 18,374 | 1.42 s | 4.32 s |

The 700 VU run recorded one dropped connection, which was not investigated.

## Findings

1. **The hold-svc connection pool was the largest bottleneck measured.**
   Raising it from 20 to 100 improved throughput by about 40% and reduced
   average latency by about 29%. Each setting was run once.

2. **The gRPC hop was not measured in isolation.** A sequential grpcurl call
   takes 25 ms end to end, including the database insert, so the hop is at
   most a small part of that. Proving its cost requires a no-op RPC
   benchmark (see Follow-up).

3. **The remaining concurrent latency is most likely database-side
   contention plus shared CPU.** Under 100 concurrent workers, average
   latency is 200 ms against 25 ms sequentially. Candidate causes are WAL
   flushes on commit, lock waits, and the load generator competing for CPU
   on the same machine. None of these were measured directly.

4. **booking-api's Hikari pool was not a bottleneck.** Its metrics showed
   zero pending connections and zero timeouts at all tested concurrency
   levels.

5. **No sign of Redis being a bottleneck.** The two `DEL` commands per hold
   executed without queueing.

6. **The distinct-seat runs do not test the correctness invariant.** Ten
   thousand holds on ten thousand different seats cannot produce a double
   booking, so these runs show throughput and stability, not correctness
   under contention. That evidence comes from the concurrency tests in
   hold-svc and the same-seat test, and should be re-verified after load
   (see Follow-up).

## Limitations

- Single machine: services, datastores, and load generators competed for
  CPU. CPU and memory per process were not recorded.
- One run per configuration, so run-to-run variance is unknown.
- PostgreSQL settings such as `max_connections` and WAL configuration were
  not recorded.

## Consequences

- `SetMaxOpenConns(100)` is appropriate for local development. In
  production, the pool size must account for the number of hold-svc
  instances and PostgreSQL's `max_connections` limit. Note that the
  PostgreSQL default is 100, which a pool of 100 plus booking-api's pool
  would exceed.
- A connection pooler (PgBouncer) should front PostgreSQL before deploying
  multiple hold-svc instances.
- The hold path was stable under sustained local load: over 25,000
  successful k6 holds with at most one error.
- No architectural change is justified by these numbers.

## Follow-up

- Verify the invariants after a load run with SQL: no seat has more than one
  `HELD` booking or more than one `CONFIRMED` booking.
- Run a concurrent test where many clients race for one fresh seat and
  confirm exactly one succeeds.
- Benchmark a no-op RPC with ghz to isolate the cost of the gRPC hop.
- Record CPU and memory per process, and PostgreSQL statistics
  (`pg_stat_statements`, WAL metrics), during runs.
- Make the pool size configurable through an environment variable.
- Instrument hold-svc with Prometheus and add OpenTelemetry spans to
  attribute latency per hop.
- Deploy to AWS ECS with per-service CPU isolation, run k6 from a separate
  host, and re-run the load test.
- Review PostgreSQL tuning (`shared_buffers`, WAL settings) for the deployed
  environment.

## Alternatives considered

- **Batch inserts.** Would require contract changes and shift complexity to
  the client. Rejected.
- **`synchronous_commit = off`.** Trades durability for throughput.
  Unacceptable for a booking system. Rejected.
- **Sharding by event.** Shifts the correctness problem to a coordination
  layer. Rejected as out of scope.
