-- Clean up hold and booking state between load-test runs.
-- Does not delete seats or events so the seed survives.

DELETE FROM bookings WHERE status IN ('HELD', 'CONFIRMED');