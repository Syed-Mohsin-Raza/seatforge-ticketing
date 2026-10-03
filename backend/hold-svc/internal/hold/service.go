package hold

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/hold-svc/internal/pb"
)

var (
	ErrSeatNotFound      = errors.New("seat not found")
	ErrSeatAlreadyHeld   = errors.New("seat already held")
	ErrSeatAlreadyBooked = errors.New("seat already confirmed")
)

type Service struct {
	db *sql.DB
}

func NewService(db *sql.DB) *Service {
	return &Service{db: db}
}

func (s *Service) ReserveSeat(ctx context.Context, req *pb.ReserveSeatRequest) (*pb.ReserveSeatResponse, error) {
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return nil, fmt.Errorf("begin tx: %w", err)
	}
	defer tx.Rollback()

	// Row lock on the seat.
	var seatID int64
	var eventID int64
	err = tx.QueryRowContext(ctx,
		`SELECT id, event_id FROM seats WHERE id = $1 FOR UPDATE`,
		req.SeatId).Scan(&seatID, &eventID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, fmt.Errorf("%w: seat_id=%d", ErrSeatNotFound, req.SeatId)
	}
	if err != nil {
		return nil, fmt.Errorf("lock seat: %w", err)
	}

	// Check for existing active booking on this seat.
	// Note: sql.ErrNoRows here means the seat is available, not missing.
	var existingStatus string
	err = tx.QueryRowContext(ctx,
    	`SELECT status FROM bookings
    	WHERE seat_id = $1 AND status IN ('HELD', 'CONFIRMED')
   	  	LIMIT 1`,
   		 req.SeatId).Scan(&existingStatus)
	if err != nil && !errors.Is(err, sql.ErrNoRows) {
    	return nil, fmt.Errorf("check existing: %w", err)
	}
	if existingStatus == "CONFIRMED" {
    	return nil, fmt.Errorf("%w: seat_id=%d", ErrSeatAlreadyBooked, req.SeatId)
	}
	if existingStatus == "HELD" {
 		return nil, fmt.Errorf("%w: seat_id=%d", ErrSeatAlreadyHeld, req.SeatId)
	}

	// Insert the hold.
	expiresAt := time.Now().Add(time.Duration(req.HoldDurationSeconds) * time.Second)

	var bookingID int64
	err = tx.QueryRowContext(ctx,
		`INSERT INTO bookings (event_id, seat_id, user_id, status, hold_expires_at, created_at, updated_at, version)
		 VALUES ($1, $2, $3, 'HELD', $4, now(), now(), 0)
		 RETURNING id`,
		eventID, req.SeatId, req.UserId, expiresAt).Scan(&bookingID)
	if err != nil {
		return nil, fmt.Errorf("insert booking: %w", err)
	}

	if err := tx.Commit(); err != nil {
		return nil, fmt.Errorf("commit: %w", err)
	}

	return &pb.ReserveSeatResponse{
		BookingId:        bookingID,
		Status:           "HELD",
		ExpiresAtEpochMs: expiresAt.UnixMilli(),
	}, nil
}

func (s *Service) ReleaseSeat(ctx context.Context, req *pb.ReleaseSeatRequest) (*pb.ReleaseSeatResponse, error) {
	result, err := s.db.ExecContext(ctx,
		`UPDATE bookings
		 SET status = 'CANCELLED', updated_at = now(), version = version + 1
		 WHERE id = $1 AND status IN ('HELD', 'CONFIRMED')`,
		req.BookingId)
	if err != nil {
		return nil, fmt.Errorf("release: %w", err)
	}

	rows, err := result.RowsAffected()
	if err != nil {
		return nil, fmt.Errorf("rows affected: %w", err)
	}

	return &pb.ReleaseSeatResponse{Released: rows > 0}, nil
}