package com.seatforge.booking.service;

import com.seatforge.booking.domain.Booking;
import com.seatforge.booking.domain.BookingStatus;
import com.seatforge.booking.domain.Seat;
import com.seatforge.booking.grpc.HoldServiceClient;
import com.seatforge.booking.repository.BookingRepository;
import com.seatforge.booking.repository.SeatRepository;
import com.seatforge.hold.v1.ReserveSeatResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class SeatReservationService {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final SeatAvailabilityCache cache;
    private final HoldServiceClient holdServiceClient;


    public SeatReservationService(SeatRepository seatRepository,
                                  BookingRepository bookingRepository,
                                  SeatAvailabilityCache cache,
                                  HoldServiceClient holdServiceClient) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.cache = cache;
        this.holdServiceClient = holdServiceClient;
    }

    public Booking holdSeat(Long seatId, String userId, Duration holdDuration) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId));

        ReserveSeatResponse grpcResp;
        try {
            grpcResp = holdServiceClient.reserveSeat(
                    seatId, userId, holdDuration);
        } catch (io.grpc.StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.ALREADY_EXISTS) {
                throw new IllegalStateException("Seat already held or confirmed: " + seatId);
            }
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw new IllegalArgumentException("Seat not found: " + seatId);
            }
            throw new IllegalStateException("Hold service error: " + e.getStatus(), e);
        }

        Booking saved = bookingRepository.findById(grpcResp.getBookingId())
                .orElseThrow(() -> new IllegalStateException(
                        "Booking not found after gRPC call: " + grpcResp.getBookingId()));

        cache.invalidate(saved.getEvent().getId());
        cache.invalidateSummary(saved.getEvent().getId());
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