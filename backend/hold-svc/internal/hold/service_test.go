package hold

import (
	"context"
	"fmt"
	"os"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/hold-svc/internal/db"
	"github.com/Syed-Mohsin-Raza/seatforge-ticketing/backend/hold-svc/internal/pb"
)

func TestReserveSeat(t *testing.T) {
	url := os.Getenv("DATABASE_URL")
	if url == "" {
		t.Skip("DATABASE_URL not set; skipping integration test")
	}

	ctx := context.Background()
	database, err := db.Open(ctx, url)
	if err != nil {
		t.Fatalf("db open: %v", err)
	}
	defer database.Close()

	// Requires seed data from Flyway dev seed migration.
	var seatID int64
	err = database.QueryRowContext(ctx, `SELECT id FROM seats LIMIT 1`).Scan(&seatID)
	if err != nil {
		t.Fatalf("no seed seats: %v", err)
	}

	// Clear any existing active bookings on this seat for a clean test.
	_, _ = database.ExecContext(ctx,
		`DELETE FROM bookings WHERE seat_id = $1 AND status IN ('HELD', 'CONFIRMED')`,
		seatID)

	svc := NewService(database)

	resp, err := svc.ReserveSeat(ctx, &pb.ReserveSeatRequest{
		EventId:             1,
		SeatId:              seatID,
		UserId:              "test-user",
		HoldDurationSeconds: 60,
	})
	if err != nil {
		t.Fatalf("reserve: %v", err)
	}
	if resp.Status != "HELD" {
		t.Fatalf("expected HELD, got %s", resp.Status)
	}

	// Second reservation should fail.
	_, err = svc.ReserveSeat(ctx, &pb.ReserveSeatRequest{
		EventId:             1,
		SeatId:              seatID,
		UserId:              "other-user",
		HoldDurationSeconds: 60,
	})
	if err == nil {
		t.Fatal("expected second reserve to fail")
	}

	// Cleanup.
	_, _ = database.ExecContext(ctx,
		`UPDATE bookings SET status = 'CANCELLED' WHERE id = $1`,
		resp.BookingId)

	_ = time.Now // silence unused import 
}

func TestReserveSeat_Concurrent(t *testing.T) {
    url := os.Getenv("DATABASE_URL")
    if url == "" {
        t.Skip("DATABASE_URL not set")
    }

    ctx := context.Background()
    database, err := db.Open(ctx, url)
    if err != nil {
        t.Fatalf("db open: %v", err)
    }
    defer database.Close()

    var seatID int64
    if err := database.QueryRowContext(ctx, `SELECT id FROM seats LIMIT 1`).Scan(&seatID); err != nil {
        t.Fatalf("no seed seats: %v", err)
    }

    // Clean slate
    if _, err := database.ExecContext(ctx,
        `DELETE FROM bookings WHERE seat_id = $1 AND status IN ('HELD','CONFIRMED')`,
        seatID); err != nil {
        t.Fatalf("cleanup: %v", err)
    }

    svc := NewService(database)

    const workers = 20
    var wg sync.WaitGroup
    var successes, failures atomic.Int32
    start := make(chan struct{})

    for i := 0; i < workers; i++ {
        wg.Add(1)
        go func(userID string) {
            defer wg.Done()
            <-start
            _, err := svc.ReserveSeat(ctx, &pb.ReserveSeatRequest{
                EventId:             1,
                SeatId:              seatID,
                UserId:              userID,
                HoldDurationSeconds: 60,
            })
            if err == nil {
                successes.Add(1)
            } else {
                failures.Add(1)
            }
        }(fmt.Sprintf("user-%d", i))
    }

    close(start)
    wg.Wait()

    if got := successes.Load(); got != 1 {
        t.Fatalf("expected exactly 1 success, got %d", got)
    }
    if got := failures.Load(); got != workers-1 {
        t.Fatalf("expected %d failures, got %d", workers-1, got)
    }
}