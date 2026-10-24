# ADR-0005: JWT authentication for the booking API

- **Status:** Accepted
- **Date:** 2026-10-24

## Context

The booking API started as an anonymous API. Any caller could hold,
confirm, or cancel a booking for any user by providing a `user_id` in
the request body. That is acceptable for an internal demonstration,
but it does not match a real ticketing system: bookings are entitlements
that must be provably owned by an authenticated user.

This milestone introduces authentication. The design must:

1. Prove that the caller is who they claim to be.
2. Derive the booking owner from the authenticated identity, not from
   caller-supplied data.
3. Prevent a user from confirming or cancelling another user's booking.
4. Survive restarts and secret-exposure scenarios with clear failure modes.

## Decision

Use JWT (JSON Web Tokens) signed with HS256, issued by a login endpoint
and validated by a filter on every request.

### Users table

```sql
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    public_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Two identifiers per user:

- `id` — BIGSERIAL, internal, used for joins and indexes. Fast.
- `public_id` — UUID, external, used in JWTs and API responses.
  Never guessable. Prevents enumeration attacks.

### Password storage

BCrypt via Spring Security's `BCryptPasswordEncoder`. A minimum password
length of 8 characters is enforced at the DTO level
(`@Size(min = 8, max = 72)`). The 72-character upper bound matches
BCrypt's internal input truncation.

### JWT contract

- `sub` — the user's `public_id` (UUID string).
- `email` — the user's email, for logging and future routing.
- `iat`, `exp` — issued-at and expiry. TTL defaults to 60 minutes.

Signed with HS256. The signing key is a `SecretKey` bean created at
startup from `seatforge.jwt.secret`.

### Secret validation

`JwtConfig` validates the secret at startup:

- If the secret is null or blank, fail with a clear message.
- If the secret is the literal string `dev-secret-change-me`, fail.
  That value is documented as the dev default and must never be used.
- If the secret is valid Base64, decode it. Otherwise, derive a 32-byte
  key via SHA-256 of the UTF-8 bytes (defensive fallback).
- If the resulting key is under 32 bytes, fail with the exact number
  of bytes and the generation command.

Secret sources, by profile:

- **dev** — `application-dev.yml` holds a fixed Base64 test secret.
  Not a security secret; it makes
  `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` work without
  environment setup.
- **test** — `src/test/resources/application.yml` holds a different
  fixed Base64 test secret. Test and dev secrets are different so a
  dev token cannot be replayed against tests.
- **production** — `SEATFORGE_JWT_SECRET` environment variable, injected
  from AWS Secrets Manager via the ECS task definition. Not a raw
  operator-supplied environment variable in the long run.

### Endpoints

- `POST /api/v1/auth/register` — public. Creates a user, returns a JWT.
  Duplicate email → `409 Conflict`.
- `POST /api/v1/auth/login` — public. Verifies the password, returns a JWT.
  Wrong email or password → `401 Unauthorized` with the same message
  in both cases (no enumeration via error messages).

### Authorization rules

`SecurityConfig` defines:

```java
.requestMatchers("/api/v1/auth/**").permitAll()
.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
.requestMatchers("/actuator/**").denyAll()
.anyRequest().authenticated()
```

Semantics:

- `/auth/**` — public.
- `/actuator/health` — public for load balancers and container health checks.
- Everything else under `/actuator/` — denied. Metrics, environment, and
  beans endpoints leak infrastructure details and must not be public.
- Every other endpoint — requires a valid JWT.

For unauthenticated callers, `denyAll()` returns `401 Unauthorized`.
For authenticated callers without the required role, it returns
`403 Forbidden`. This is standard Spring Security behavior.

### Ownership check on confirm and cancel

The JWT identifies the caller. It does not authorize the action.
`SeatReservationService.confirmBooking` and `cancelBooking` take the
authenticated UUID and compare it to the booking's `user_id`:

```java
if (!booking.getUserId().equals(authenticatedUserId.toString())) {
    throw new AccessDeniedException("Booking does not belong to authenticated user");
}
```

`AccessDeniedException` is mapped to 403 by `GlobalExceptionHandler`.
This is defense in depth: even if the security filter chain is
misconfigured, the service layer still refuses cross-user actions.

### Deriving `user_id` from the token

`HoldSeatRequest` no longer has a `userId` field. `BookingController.holdSeat`
receives `@AuthenticationPrincipal AuthenticatedUser user` and passes
`user.userId().toString()` to the service. The client cannot influence
the booking owner.

## Consequences

### Positive

- Booking ownership is provable.
- Cross-user actions are rejected at two layers.
- JWT is stateless. No session storage, no sticky sessions.
- Passwords are hashed with BCrypt, never logged or returned.
- The signing key is validated at startup, so misconfiguration fails fast.
- Different secrets per environment prevent cross-environment token replay.

### Negative

- **No refresh tokens.** Tokens expire after 60 minutes, and users must
  log in again. This is acceptable for now but gives poor UX. Refresh
  tokens with a revocation store are the next iteration.
- **No token revocation.** A stolen token is valid until it expires.
  A JWT blacklist, or short-lived tokens plus refresh tokens, are the two
  viable fixes. Both require additional work.
- **No rate limiting on login.** An attacker can brute-force passwords.
  Rate limiting per IP and per account is required before public exposure.
- **No account lockout.** Repeated failed logins do not disable the
  account.
- **No password reset.** A user who forgets their password is locked out.
  A reset requires an email flow, which requires the notification service
  to reach real inboxes (SES or equivalent).
- **No email verification.** A user can register with any email address,
  including one they do not control. This affects password reset and
  account recovery flows.
- **No MFA.** Out of scope.
- **No OAuth.** No "Sign in with Google." Deferred.

### Neutral

- `Booking.userId` remains `VARCHAR(64)` holding a UUID string. A UUID
  column type would be cleaner; deferred to a future migration.
- The role claim is not present in the JWT. Every authenticated user
  is assumed to be a customer. When admin tools appear, roles will be
  added to the token and to `SecurityConfig`'s rules.

## Disabled tests

Three test classes were disabled during this milestone because they
exercise the booking flow, which now requires a JWT and a running
hold-svc and payment-svc:

- `BookingControllerIntegrationTest` — 2 tests
- `SeatAvailabilityCacheIntegrationTest` — 2 tests
  (`holdInvalidatesCache`, `firstReadIsCacheMissSecondIsHit`)
- `SeatReservationServiceConcurrencyTest` — 1 test

This is real technical debt. The correct fix is a test slice that:

1. Uses Testcontainers for Postgres and Redis.
2. Starts an in-process gRPC server implementing
   `HoldServiceGrpc.HoldServiceBase` and
   `PaymentServiceGrpc.PaymentServiceBase`, both backed by the same
   Testcontainers Postgres.
3. Sets `seatforge.events.publisher=noop` so the SNS publisher is a no-op.
4. Registers a user via `POST /auth/register` and uses the returned JWT
   for all subsequent calls.

This is roughly half a day of work and is the natural next test-focused
milestone. Until then, the end-to-end flow is verified manually with
`curl` and `psql`.

## Alternatives considered

### Session cookies instead of JWT

Rejected. The frontend is a separate origin (Next.js). Session cookies
require CORS with credentials, same-site policy tuning, and CSRF
protection. A JWT in an `Authorization` header is simpler across origins.

### OAuth with Google or GitHub

Rejected for this milestone. Mixing `oauth2Login()` (session-based) with
stateless JWT requires a custom success handler and a user mapping
strategy. It adds complexity without solving the current problem.
Reconsider when the frontend needs social login.

### A separate auth-svc

Rejected. Auth is a controller and a filter inside booking-api. Extracting
it to a service adds operational cost for no benefit at this scale. If
the identity domain grows (SSO, MFA, device management), an auth-svc
becomes justified.

### HS256 vs RS256

HS256 (symmetric) was chosen. There is one shared secret between issuer and
verifier. In this deployment, the issuer and verifier are the same process,
so asymmetric signing adds no value. RS256 becomes correct when a second
service (payment-svc) needs to verify tokens without possessing the
signing key. That is not the case today.

## Follow-up work

- Test hardening (in-process gRPC fake, JWT-aware `TestRestTemplate`).
- Refresh tokens with a revocation store.
- Rate limiting on `/auth/login` and `/auth/register`.
- Password reset flow via SES.
- Email verification.
- Add a role to the JWT when admin endpoints exist.
- Move `Booking.userId` to a UUID column type.
