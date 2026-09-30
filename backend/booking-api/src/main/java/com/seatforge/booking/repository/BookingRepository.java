package com.seatforge.booking.repository;

import com.seatforge.booking.domain.Booking;
import com.seatforge.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findBySeatIdAndStatus(Long seatId, BookingStatus status);

    List<Booking> findByStatusAndHoldExpiresAtBefore(BookingStatus status, Instant cutoff);
}