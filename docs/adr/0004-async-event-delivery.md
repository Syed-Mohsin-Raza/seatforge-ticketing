# ADR-0004: Asynchronous event delivery with SNS and SQS

- **Status:** Accepted
- **Date:** 2026-10-17

## Context

Booking state changes (hold, confirm, cancel) must trigger downstream
actions: sending confirmation emails, updating analytics, and future
fraud detection. These actions are not part of the booking transaction
and must not block the HTTP response.

The question is how to deliver the event from `booking-api` (Java) to
`notification-svc` (Go), and what guarantees to make about delivery.

## Decision

Use SNS as a fan-out topic and SQS as the delivery queue. Booking-api
publishes a JSON event to the `seatforge-bookings` SNS topic. One or
more SQS queues subscribe to the topic; each queue has its own consumer
and its own DLQ.

For this milestone:

- **One queue:** `seatforge-notifications`, consumed by notification-svc.
- **One DLQ:** `seatforge-notifications-dlq`, with a redrive policy
  that moves messages after 3 failed receives.
- **Publish after commit:** the event is published after the state
  change is committed, not before.
- **Best-effort delivery:** publish failures are logged and swallowed.
  The user's HTTP request is not failed.
- **`eventType` message attribute:** the discriminator that identifies
  the event kind. Consumers filter on it without parsing the body.
- **JSON body:** human-readable, text-native, inspectable in the DLQ.

## Rationale

### Why SNS + SQS instead of SQS alone?

SNS fan-out lets multiple queues subscribe to one topic. When
`analytics-svc` or `fraud-svc` are added, they create their own queue,
subscribe to the same topic, and receive their own copy. The publisher
does not need to know about them.

With direct SQS, booking-api would need to send to N queues and manage
N sends per event. That couples the publisher to the number of consumers
and violates the open-closed principle at the infrastructure level.

### Why publish after commit?

The alternative is to publish inside the transaction. Two failure modes:

1. **Publish inside the transaction, commit fails:** the event was
   published, but the booking state rolled back. The consumer receives
   a ghost event for a booking that does not exist.
2. **Publish after commit, publish fails:** the booking is committed,
   but the event is lost. The user does not receive a confirmation
   email.

A ghost event is worse than a missing event. The first confuses
operations and the user. The second is an annoyance. **Choose publish
after commit.**

### Why best-effort, not synchronous-fail?

If publish failure rolls back the booking, the user loses their seat
because SNS is unavailable. That is unacceptable. The seat reservation
is the primary concern; the notification is secondary. Fail-safe.

### Why the `eventType` in the message attribute?

Two reasons:

1. **SQS filtering.** A future consumer can subscribe with a filter
   policy that only routes `BOOKING_CONFIRMED` events to it. No code
   change on the publisher side.
2. **Separation of concerns.** The body is the domain payload. Its
   shape is dictated by the domain, not by the routing mechanism.
   The discriminator lives in transport metadata.

### Why JSON?

Text-native, human-inspectable in the DLQ, and the payload overhead is
negligible for a few hundred bytes. Protobuf would require a schema
on hand to inspect a stuck message. JSON lets an on-call engineer
read it in one command.

## Consequences

### Positive

- Booking-api is decoupled from notification-svc. Each can be deployed
  and scaled independently.
- Adding consumers requires zero changes to booking-api.
- Failed messages land in a DLQ for manual inspection.
- The publish is best-effort and never fails the user's request.

### Negative

- **Missing event window.** If the publish fails after commit, the
  event is lost. There is no automatic retry. Operations must inspect
  logs to find missing events.
- **Consumer at-least-once delivery.** SQS guarantees at-least-once,
  not exactly-once. If the consumer crashes after processing but
  before deleting, the message is redelivered. Handlers must be
  idempotent. Currently the notification handlers just log, so
  duplicate logs are acceptable. Real email sending must dedupe on
  the event's `eventId`.
- **No ordering guarantee.** SQS standard queues do not guarantee
  order. A `BOOKING_CONFIRMED` event may arrive before its
  `BOOKING_HELD` event under load. Consumers must be tolerant of
  out-of-order arrival. FIFO queues are available but require
  message group IDs and are slower. Not used in this milestone.

### Neutral

- LocalStack is used for local development. In production, the same
  SNS and SQS resources are provisioned via AWS CDK.

## Alternatives considered

### Transactional outbox pattern

Write the event to an `outbox` table inside the same database
transaction as the state change. A separate process polls the outbox
and publishes to SNS. This eliminates the missing-event window.

**Deferred.** It requires a new table, a publisher process, and
at-least-once delivery from the outbox. It is the correct next
iteration when missing events become a real problem. For now, the
window is small and the consequence is a missing email.

### Publish synchronously with rollback on failure

Rejected. The user loses the seat if SNS is down.

### Direct SQS from booking-api

Rejected. Couples the publisher to the consumer count.

### Kafka or RabbitMQ

Rejected. Adds operational complexity for no benefit at this scale.
SNS + SQS is the managed AWS-native answer.

### Event sourcing

Rejected. Overkill for this domain. The booking table is the
authoritative state. Events are side effects, not the source of truth.

## Follow-up work

- **SES integration.** Replace the log statements in
  `notification-svc/internal/handler/booking.go` with actual SES
  `SendEmail` calls. LocalStack supports SES.
- **Idempotency in the consumer.** Deduplicate on `eventId`. A
  `processed_events` table or an in-memory LRU cache in Redis.
- **Outbox pattern.** Eliminate the missing-event window.
- **Analytics queue.** Add a second SQS queue subscribed to the same
  SNS topic for future analytics-svc.
- **FIFO ordering.** Only if out-of-order becomes a demonstrated
  problem.