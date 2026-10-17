package event

import (
	"encoding/json"
	"fmt"
	"time"
)

// Envelope carries just enough of the event to route it.
type Envelope struct {
	EventID    string    `json:"eventId"`
	EventType  string    `json:"eventType"`
	OccurredAt time.Time `json:"occurredAt"`
}

type BookingHeldPayload struct {
	EventID       string    `json:"eventId"`
	OccurredAt    time.Time `json:"occurredAt"`
	BookingID     int64     `json:"bookingId"`
	SeatID        int64     `json:"seatId"`
	DomainEventID int64     `json:"domainEventId"`
	UserID        string    `json:"userId"`
	HoldExpiresAt time.Time `json:"holdExpiresAt"`
}

type BookingConfirmedPayload struct {
	EventID                string    `json:"eventId"`
	OccurredAt             time.Time `json:"occurredAt"`
	BookingID              int64     `json:"bookingId"`
	SeatID                 int64     `json:"seatId"`
	DomainEventID          int64     `json:"domainEventId"`
	UserID                 string    `json:"userId"`
	PaymentAuthorizationID string    `json:"paymentAuthorizationId"`
}

type BookingCancelledPayload struct {
	EventID       string    `json:"eventId"`
	OccurredAt    time.Time `json:"occurredAt"`
	BookingID     int64     `json:"bookingId"`
	SeatID        int64     `json:"seatId"`
	DomainEventID int64     `json:"domainEventId"`
	UserID        string    `json:"userId"`
}

type ParseError struct {
	Reason string
}

func (e *ParseError) Error() string {
	return "event parse error: " + e.Reason
}

func ParseEnvelope(body string) (*Envelope, error) {
	var env Envelope
	if err := json.Unmarshal([]byte(body), &env); err != nil {
		return nil, &ParseError{Reason: fmt.Sprintf("invalid JSON: %v", err)}
	}
	if env.EventID == "" {
		return nil, &ParseError{Reason: "missing eventId"}
	}
	return &env, nil
}

func ParseBookingHeld(body string) (*BookingHeldPayload, error) {
	var p BookingHeldPayload
	if err := json.Unmarshal([]byte(body), &p); err != nil {
		return nil, &ParseError{Reason: err.Error()}
	}
	return &p, nil
}

func ParseBookingConfirmed(body string) (*BookingConfirmedPayload, error) {
	var p BookingConfirmedPayload
	if err := json.Unmarshal([]byte(body), &p); err != nil {
		return nil, &ParseError{Reason: err.Error()}
	}
	return &p, nil
}

func ParseBookingCancelled(body string) (*BookingCancelledPayload, error) {
	var p BookingCancelledPayload
	if err := json.Unmarshal([]byte(body), &p); err != nil {
		return nil, &ParseError{Reason: err.Error()}
	}
	return &p, nil
}