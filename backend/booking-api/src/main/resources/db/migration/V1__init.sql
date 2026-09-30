CREATE TABLE events (
                        id          BIGSERIAL PRIMARY KEY,
                        name        VARCHAR(255)    NOT NULL,
                        venue       VARCHAR(255)    NOT NULL,
                        starts_at   TIMESTAMPTZ     NOT NULL,
                        created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE seats (
                       id          BIGSERIAL PRIMARY KEY,
                       event_id    BIGINT          NOT NULL REFERENCES events(id),
                       section     VARCHAR(64)     NOT NULL,
                       row_label   VARCHAR(16)     NOT NULL,
                       seat_number INTEGER         NOT NULL,
                       price_cents INTEGER         NOT NULL,
                       CONSTRAINT uq_seat_position UNIQUE (event_id, section, row_label, seat_number)
);

CREATE TABLE bookings (
                          id          BIGSERIAL PRIMARY KEY,
                          event_id    BIGINT          NOT NULL REFERENCES events(id),
                          seat_id     BIGINT          NOT NULL REFERENCES seats(id),
                          user_id     VARCHAR(64)     NOT NULL,
                          status      VARCHAR(16)     NOT NULL,
                          hold_expires_at TIMESTAMPTZ,
                          created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
                          updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
                          version     BIGINT          NOT NULL DEFAULT 0,
                          CONSTRAINT chk_booking_status CHECK (status IN ('HELD','CONFIRMED','EXPIRED','CANCELLED'))
);

-- Critical: one confirmed booking per seat. Partial unique index.
CREATE UNIQUE INDEX uq_confirmed_booking_per_seat
    ON bookings (seat_id)
    WHERE status = 'CONFIRMED';

-- One active hold per seat. Prevents two concurrent holds.
CREATE UNIQUE INDEX uq_active_hold_per_seat
    ON bookings (seat_id)
    WHERE status = 'HELD';

CREATE INDEX idx_bookings_user ON bookings (user_id);
CREATE INDEX idx_bookings_event_status ON bookings (event_id, status);