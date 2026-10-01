package com.seatforge.booking.repository;

import com.seatforge.booking.domain.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") Long id);

    List<Seat> findByEventId(Long eventId);

    @Query("""
    SELECT s,
           (SELECT b.status FROM Booking b
            WHERE b.seat.id = s.id
              AND b.status IN ('HELD', 'CONFIRMED')
            ORDER BY b.id DESC
            LIMIT 1)
    FROM Seat s
    WHERE s.event.id = :eventId
    """)
    List<Object[]> findSeatsWithStatus(@Param("eventId") Long eventId);

    @Query(value = """
    SELECT
        s.section,
        COUNT(*) FILTER (WHERE b.status IS NULL OR b.status NOT IN ('HELD', 'CONFIRMED')) AS available,
        COUNT(*) FILTER (WHERE b.status = 'HELD')     AS held,
        COUNT(*) FILTER (WHERE b.status = 'CONFIRMED') AS confirmed,
        COUNT(*)                                       AS total
    FROM seats s
    LEFT JOIN (
        SELECT DISTINCT ON (seat_id) seat_id, status
        FROM bookings
        WHERE status IN ('HELD', 'CONFIRMED')
        ORDER BY seat_id, id DESC
    ) b ON b.seat_id = s.id
    WHERE s.event_id = :eventId
    GROUP BY s.section
    ORDER BY s.section
    """, nativeQuery = true)
    List<Object[]> summarizeBySection(@Param("eventId") Long eventId);
}