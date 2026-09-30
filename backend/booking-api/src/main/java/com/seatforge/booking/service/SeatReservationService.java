package com.seatforge.booking.service;

import com.seatforge.booking.domain.Booking;
import com.seatforge.booking.domain.BookingStatus;
import com.seatforge.booking.domain.Seat;
import com.seatforge.booking.repository.BookingRepository;
import com.seatforge.booking.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class SeatReservationService {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;

    public SeatReservationService(SeatRepository seatRepository, BookingRepository bookingRepository) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public Booking holdSeat(Long seatId, String userId, Duration holdDuration) {
        // Pessimistic lock — blocks other transactions until this one commits
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId));

        // Check for existing confirmed booking
        bookingRepository.findBySeatIdAndStatus(seatId, BookingStatus.CONFIRMED)
                .ifPresent(b -> {
                    throw new IllegalStateException("Seat already confirmed: " + seatId);
                });

        // Check for existing active hold
        bookingRepository.findBySeatIdAndStatus(seatId, BookingStatus.HELD)
                .ifPresent(b -> {
                    throw new IllegalStateException("Seat already held: " + seatId);
                });

        Booking booking = Booking.hold(seat.getEvent(), seat, userId, Instant.now().plus(holdDuration));
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking confirmBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        booking.confirm();
        return bookingRepository.save(booking);
    }

    @Transactional
    public Booking cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        booking.cancel();
        return bookingRepository.save(booking);
    }
}