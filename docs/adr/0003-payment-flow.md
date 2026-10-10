# ADR-0003: Payment authorization and capture

- **Status:** Accepted
- **Date:** 2026-10-10

## Context

A confirmed booking requires payment. The confirm flow must:

1. Prevent double-charging a user for the same booking.
2. Handle the case where the seat is held but payment fails.
3. Handle the case where authorization succeeds but booking confirmation fails.
4. Handle the case where both succeed but capture fails.
5. Be retryable without side effects.

`booking-api` and `payment-svc` are separate services connected by gRPC.
Each has its own database schema. There is no distributed transaction
coordinator.

## Decision

Use a three-step payment flow within a single confirm request.

Before step 1, `booking-api` checks that the booking is `HELD` and the hold
has not expired. The amount is read from the seat on the server side and is
never taken from the client.

1. **Authorize.** `booking-api` calls
   `payment-svc.Authorize(booking_id, user_id, amount_cents, idempotency_key)`.
   `payment-svc` records a `Payment` row with `status = AUTHORIZED`.
2. **Confirm.** If authorization succeeds, `booking-api` transitions the
   booking to `CONFIRMED` and stores the `paymentAuthorizationId` on the
   booking row, in a short local transaction. The existing partial unique
   indexes and the `@Version` check still enforce the booking invariants.
3. **Capture.** After confirmation commits, `booking-api` calls
   `payment-svc.Capture(authorization_id)` as a best-effort operation.
   `payment-svc` moves the payment to `status = CAPTURED`.

### Failure handling

| Failure | Resulting state | Recovery |
|---|---|---|
| Step 1 fails (declined) | Booking `HELD`, no authorization | API returns `402 Payment Required` |
| Step 2 fails after step 1 | Booking `HELD`, payment `AUTHORIZED` | Retry returns the same authorization (idempotency key); otherwise the authorization must be voided |
| Step 3 fails | Booking `CONFIRMED`, payment `AUTHORIZED` | Failure is logged; the reconciliation job retries capture |
| Process crash between steps | One of the states above | Retry is safe through the idempotency key |

### Idempotency

`booking-api` sends a deterministic idempotency key:
`"booking-{bookingId}-auth"`.

`payment-svc` enforces uniqueness with a partial index on
`payment.payments(idempotency_key)`. A retry with the same key returns the
original payment row without creating a new one. A retry with the same key
but a different amount must be rejected, not silently accepted.

`booking-api` also short-circuits a confirm call if the booking is already
`CONFIRMED`.

### Transaction boundary

`confirmBooking` does not use `@Transactional` around the whole flow. A
gRPC call must not hold a Hikari connection across a network round-trip.
Step 2 runs in its own short transaction. Associations needed after the
network calls (such as `Booking.seat` and `Booking.event`) are loaded
eagerly in the query, because lazy loading outside a transaction fails.

### Error mapping

| Condition | gRPC status | HTTP status |
|---|---|---|
| Payment declined by provider | `FAILED_PRECONDITION` | 402 |
| Authorization not found | `NOT_FOUND` | 404 |
| Invalid request | `INVALID_ARGUMENT` | 400 |
| payment-svc unreachable or timed out | `UNAVAILABLE` / `DEADLINE_EXCEEDED` | 503 |
| Unexpected | `INTERNAL` | 500 |

A timeout on `Authorize` leaves the outcome unknown. The client retries with
the same idempotency key, which resolves the ambiguity. Capture errors are
logged and not returned to the client.

## Rationale

### Why not a distributed transaction?

XA and 2PC require a transaction manager that spans both services and both
database connections. The operational complexity is high, and a coordinator
crash leaves participants in an uncertain state, which is the problem we are
trying to avoid.

A saga with compensating transactions is the right long-term answer for a
flow with more steps. For three steps and a small set of failure modes, it
is premature.

### Why authorize before capture?

Authorization is reversible. Capture is not, without a refund. Putting the
reversible step first means payment failures are caught before the booking
is confirmed. This is the standard pattern in card payment systems.

### Why capture after confirmation?

Capture commits funds. If capture happened before confirmation and
confirmation failed, a refund would be needed. This order ensures the
booking state is durable before funds are committed.

### Why is capture best-effort, and how does this differ from confirm-then-charge?

By step 3 the funds are already authorized, so the user's ability to pay was
verified and the money is reserved. A failed capture is then a delivery
problem that can be retried, not a credit problem. This is what separates
this design from the rejected confirm-then-charge alternative, where a
confirmed booking could exist with no payment guarantee at all.

The guarantee is time-limited. Real providers release an authorization after
a window (commonly several days). The reconciliation job must capture within
that window, so it is a hard requirement before real money is involved, not
an optional improvement.

Rolling back a confirmed booking because capture failed would create a
different failure: the user loses the seat while the payment is still
reserved. It is better to keep the booking and reconcile the payment.

## Consequences

### Positive

- No distributed transaction. Each service owns its own state.
- Confirm is idempotent and safe to retry.
- Payment failures are visible as `402` responses.
- The confirm path is a short sequence that is easy to reason about.

### Negative

- A booking can be `CONFIRMED` with a payment that is not captured. Until
  reconciliation runs, the two states disagree. This is normally brief, but
  it lasts as long as capture keeps failing.
- An authorization can be left without a booking if step 2 fails or the hold
  expires during the flow. It needs a void or must expire at the provider.
- Two gRPC calls per confirm (authorize and capture), so latency is higher
  than a single in-process call.
- Reconciliation does not exist yet, so the failure cases above are
  currently handled only by logging.

### Neutral

- The payment provider is abstracted behind an interface. Replacing
  `MockPaymentProvider` with Stripe is a new implementation, not a refactor
  of the payment flow.

## Alternatives considered

### Charge-then-confirm

Authorize and capture in one step before confirming the booking. If
confirmation fails, no booking exists but money has been taken. Rejected
because capture is irreversible and would need a refund path for every
failure.

### Confirm-then-charge

Confirm the booking, then attempt to charge. Rejected because a confirmed
booking with a failed charge leaves the user with a seat they did not pay
for and no reserved funds.

### Saga with compensation

Full saga with compensating transactions in both services. Rejected as
premature for three steps. It is the right answer for flows with many
steps, such as event cancellation or partial refunds.

### Distributed transaction (XA)

Rejected for operational complexity. Idempotency and explicit ordering give
eventual consistency with reconciliation, which is sufficient here, but not
the atomicity XA would provide.

## Follow-up work

- **Reconciliation job.** A scheduled task in payment-svc that scans for
  `AUTHORIZED` payments older than N minutes and captures or voids them. N
  must be well inside the provider's authorization window. Not yet
  implemented.
- **Void on confirm failure, expiry, and cancel.** Call `payment-svc.Void`
  when step 2 fails, when a hold expires after authorization, and when a
  booking is cancelled before capture. Not yet implemented.
- **Retry on capture failure.** Currently logged and left. A retry queue is
  the natural upgrade.
- **Declined-retry semantics.** With a stable key per booking, a retry after
  a declined authorization returns the original declined result, so the user
  could not retry with another payment method. Confirm how declined rows
  are handled and document the rule.
- **Webhook from provider.** The mock provider is synchronous. A real
  provider pushes events, which adds an endpoint and a different
  idempotency model.
