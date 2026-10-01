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
    private final SeatAvailabilityCache cache;

    public SeatReservationService(SeatRepository seatRepository, BookingRepository bookingRepository, SeatAvailabilityCache cache) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.cache = cache;
    }

    @Transactional
    public Booking holdSeat(Long seatId, String userId, Duration holdDuration) {
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId));

        bookingRepository.findBySeatIdAndStatus(seatId, BookingStatus.CONFIRMED)
                .ifPresent(b -> { throw new IllegalStateException("Seat already confirmed: " + seatId); });
        bookingRepository.findBySeatIdAndStatus(seatId, BookingStatus.HELD)
                .ifPresent(b -> { throw new IllegalStateException("Seat already held: " + seatId); });

        Booking booking = Booking.hold(seat.getEvent(), seat, userId, Instant.now().plus(holdDuration));
        Booking saved = bookingRepository.save(booking);
        Long eventId = seat.getEvent().getId();
        cache.invalidate(eventId);
        cache.invalidateSummary(eventId);
        return saved;
    }

    @Transactional
    public Booking confirmBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        booking.confirm();
        Booking saved = bookingRepository.save(booking);
        Long eventId = saved.getEvent().getId();
        cache.invalidate(eventId);
        cache.invalidateSummary(eventId);
        return saved;
    }

    @Transactional
    public Booking cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        booking.cancel();
        Booking saved = bookingRepository.save(booking);
        Long eventId = saved.getEvent().getId();
        cache.invalidate(eventId);
        cache.invalidateSummary(eventId);
        return saved;
    }
}