package com.seatforge.booking.repository;

import com.seatforge.booking.domain.Booking;
import com.seatforge.booking.domain.BookingStatus;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findBySeatIdAndStatus(Long seatId, BookingStatus status);

    List<Booking> findByStatusAndHoldExpiresAtBefore(BookingStatus status, Instant cutoff);

    @Query("""
    SELECT b.seat.id, b.status
    FROM Booking b
    WHERE b.seat.id IN :seatIds
      AND b.status IN ('HELD', 'CONFIRMED')
      AND b.id = (SELECT MAX(b2.id) FROM Booking b2
                  WHERE b2.seat.id = b.seat.id
                    AND b2.status IN ('HELD', 'CONFIRMED'))
    """)
    List<Object[]> findLatestActiveStatuses(@Param("seatIds") List<Long> seatIds);
}