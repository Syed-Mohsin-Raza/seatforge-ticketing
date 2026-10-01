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
}