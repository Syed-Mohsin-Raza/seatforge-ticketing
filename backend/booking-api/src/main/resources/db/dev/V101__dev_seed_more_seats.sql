-- Extend the dev seed to 100,000 seats across sections A-J.
-- Called out as a separate migration to avoid changing V100's checksum.

INSERT INTO seats (event_id, section, row_label, seat_number, price_cents)
SELECT
    1,
    chr(65 + (gs / 10000)::int),   -- section A-J
    ((gs % 10000) / 100)::text,     -- row
    (gs % 100)::int + 1,            -- seat number
    5000
FROM generate_series(1001, 100000) AS gs
    ON CONFLICT (event_id, section, row_label, seat_number) DO NOTHING;