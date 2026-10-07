-- Payment schema for SeatForge.
-- Managed by payment-svc. The schema itself is created by Flyway
-- via spring.flyway.schemas and create-schemas=true, so the
-- CREATE SCHEMA statement is intentionally omitted here.

CREATE TABLE payment.payments (
                                  id                  BIGSERIAL PRIMARY KEY,
                                  booking_id          BIGINT NOT NULL,
                                  user_id             VARCHAR(64) NOT NULL,
                                  amount_cents        BIGINT NOT NULL,
                                  status              VARCHAR(32) NOT NULL,
                                  authorization_id    VARCHAR(64) NOT NULL UNIQUE,
                                  provider_reference  VARCHAR(128),
                                  idempotency_key     VARCHAR(64) NOT NULL UNIQUE,
                                  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  version             BIGINT NOT NULL DEFAULT 0,
                                  CONSTRAINT chk_payment_status CHECK (
                                      status IN ('AUTHORIZED', 'CAPTURED', 'VOIDED', 'CAPTURE_FAILED')
                                      )
);

CREATE INDEX idx_payments_booking ON payment.payments (booking_id);
CREATE INDEX idx_payments_user    ON payment.payments (user_id);
CREATE INDEX idx_payments_status  ON payment.payments (status);