package handler

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/notification-svc/internal/event"
)

// NewBookingHandlers returns a map of event type to handler for booking events.
func NewBookingHandlers(logger *slog.Logger) map[string]Handler {
	return map[string]Handler{
		"BOOKING_HELD":      onBookingHeld(logger),
		"BOOKING_CONFIRMED": onBookingConfirmed(logger),
		"BOOKING_CANCELLED": onBookingCancelled(logger),
	}
}

func onBookingHeld(logger *slog.Logger) Handler {
	return func(ctx context.Context, body string) error {
		p, err := event.ParseBookingHeld(body)
		if err != nil {
			return fmt.Errorf("parse BOOKING_HELD: %w", err)
		}
		logger.Info("booking held",
			"bookingId", p.BookingID,
			"userId", p.UserID,
			"seatId", p.SeatID,
			"holdExpiresAt", p.HoldExpiresAt.Format("2006-01-02T15:04:05Z07:00"),
		)
		// TODO: send a hold confirmation email
		return nil
	}
}

func onBookingConfirmed(logger *slog.Logger) Handler {
	return func(ctx context.Context, body string) error {
		p, err := event.ParseBookingConfirmed(body)
		if err != nil {
			return fmt.Errorf("parse BOOKING_CONFIRMED: %w", err)
		}
		logger.Info("booking confirmed",
			"bookingId", p.BookingID,
			"userId", p.UserID,
			"seatId", p.SeatID,
			"paymentAuthorizationId", p.PaymentAuthorizationID,
		)
		// TODO: send a confirmation email with the seat details
		return nil
	}
}

func onBookingCancelled(logger *slog.Logger) Handler {
	return func(ctx context.Context, body string) error {
		p, err := event.ParseBookingCancelled(body)
		if err != nil {
			return fmt.Errorf("parse BOOKING_CANCELLED: %w", err)
		}
		logger.Info("booking cancelled",
			"bookingId", p.BookingID,
			"userId", p.UserID,
			"seatId", p.SeatID,
		)
		// TODO: send a cancellation email
		return nil
	}
}