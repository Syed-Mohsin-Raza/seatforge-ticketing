package com.seatforge.booking.service;

import com.seatforge.booking.domain.Booking;
import com.seatforge.booking.domain.BookingStatus;
import com.seatforge.booking.domain.Seat;
import com.seatforge.booking.grpc.HoldServiceClient;
import com.seatforge.booking.grpc.PaymentServiceClient;
import com.seatforge.booking.repository.BookingRepository;
import com.seatforge.booking.repository.SeatRepository;
import com.seatforge.booking.service.exceptions.PaymentRequiredException;
import com.seatforge.hold.v1.ReserveSeatResponse;
import com.seatforge.payment.v1.AuthorizeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class SeatReservationService {

    private static final Logger log = LoggerFactory.getLogger(SeatReservationService.class);

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final SeatAvailabilityCache cache;
    private final HoldServiceClient holdServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    public SeatReservationService(SeatRepository seatRepository,
                                  BookingRepository bookingRepository,
                                  SeatAvailabilityCache cache,
                                  HoldServiceClient holdServiceClient,
                                  PaymentServiceClient paymentServiceClient) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.cache = cache;
        this.holdServiceClient = holdServiceClient;
        this.paymentServiceClient = paymentServiceClient;
    }

    public Booking holdSeat(Long seatId, String userId, Duration holdDuration) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId));

        ReserveSeatResponse grpcResp;
        try {
            grpcResp = holdServiceClient.reserveSeat(seatId, userId, holdDuration);
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

    /**
     * Confirms a held booking.
     * Flow:
     *   1. Validate the booking is HELD and not expired.
     *   2. Authorize payment via payment-svc.
     *   3. Transition booking to CONFIRMED and store the authorization ID.
     *   4. Capture payment (best-effort).
     *   5. Invalidate caches.
     * The gRPC calls happen outside a Java transaction so a Hikari
     * connection is not held across the network round-trip. The
     * booking state transition at step 3 uses a short-lived transaction
     * via the repository's save.
     * Idempotency:
     *   - If already CONFIRMED, return immediately (no double-charge).
     *   - The idempotency key sent to payment-svc is deterministic
     *     ("booking-{id}-auth"), so retries hit the same authorization.
     */
    public Booking confirmBooking(Long bookingId) {
        Booking booking = bookingRepository.findByIdWithSeatAndEvent(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            log.info("Confirm idempotent hit for booking {}", bookingId);
            return booking;
        }

        if (booking.getStatus() != BookingStatus.HELD) {
            throw new IllegalStateException(
                    "Only HELD bookings can be confirmed, current status: " + booking.getStatus());
        }

        if (booking.getHoldExpiresAt() != null
                && Instant.now().isAfter(booking.getHoldExpiresAt())) {
            throw new IllegalStateException("Hold has expired");
        }

        long amountCents = booking.getSeat().getPriceCents();
        String idempotencyKey = "booking-" + bookingId + "-auth";

        AuthorizeResponse auth;
        try {
            auth = paymentServiceClient.authorize(
                    bookingId, booking.getUserId(), amountCents, idempotencyKey);
        } catch (io.grpc.StatusRuntimeException e) {
            log.warn("Authorize failed for booking {}: {}",
                    bookingId, e.getStatus().getDescription());
            throw new PaymentRequiredException(
                    "Payment authorization failed: " + e.getStatus().getDescription(), e);
        } catch (Exception e) {
            log.error("Authorize call threw unexpected exception for booking {}: {}",
                    bookingId, e.getMessage(), e);
            throw new PaymentRequiredException(
                    "Payment authorization failed: " + e.getMessage(), e);
        }

        booking.confirm();
        booking.setPaymentAuthorizationId(auth.getAuthorizationId());
        Booking saved = bookingRepository.save(booking);

        try {
            paymentServiceClient.capture(auth.getAuthorizationId());
        } catch (Exception e) {
            log.error("Capture failed for booking {} auth={}: {}",
                    bookingId, auth.getAuthorizationId(), e.getMessage(), e);
        }

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