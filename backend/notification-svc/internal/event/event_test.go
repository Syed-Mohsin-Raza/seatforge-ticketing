package event

import (
	"testing"
)

func TestParseEnvelope(t *testing.T) {
	body := `{"eventId":"abc","eventType":"BOOKING_HELD","occurredAt":"2026-01-01T00:00:00Z"}`
	env, err := ParseEnvelope(body)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if env.EventID != "abc" {
		t.Errorf("eventId = %q, want abc", env.EventID)
	}
	if env.EventType != "BOOKING_HELD" {
		t.Errorf("eventType = %q, want BOOKING_HELD", env.EventType)
	}
}

func TestParseEnvelopeMissingEventId(t *testing.T) {
    body := `{"eventType":"BOOKING_HELD"}`
    _, err := ParseEnvelope(body)
    if err == nil {
        t.Fatal("expected error for missing eventId")
    }
}

func TestParseBookingHeld(t *testing.T) {
	body := `{"eventId":"abc","bookingId":1,"seatId":2,"userId":"u1"}`
	p, err := ParseBookingHeld(body)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if p.BookingID != 1 || p.SeatID != 2 || p.UserID != "u1" {
		t.Errorf("payload fields wrong: %+v", p)
	}
}