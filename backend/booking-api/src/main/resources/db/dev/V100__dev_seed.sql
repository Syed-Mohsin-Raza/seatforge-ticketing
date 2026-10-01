-- Dev-only seed data for local development and load testing.
-- Activated only under the 'dev' profile. Never applied in production.

INSERT INTO events (id, name, venue, starts_at)
VALUES (1, 'Load Test Concert', 'Test Arena', now() + interval '30 days')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO seats (event_id, section, row_label, seat_number, price_cents)
SELECT 1, 'A', '1', gs, 5000
FROM generate_series(1, 1000) AS gs
    ON CONFLICT (event_id, section, row_label, seat_number) DO NOTHING;

-- Reset the sequence so future inserts do not collide with id=1
SELECT setval(pg_get_serial_sequence('events', 'id'), (SELECT MAX(id) FROM events));