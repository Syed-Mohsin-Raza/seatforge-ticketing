package com.seatforge.booking.web;

import com.seatforge.booking.domain.Booking;
import com.seatforge.booking.security.AuthenticatedUser;
import com.seatforge.booking.service.SeatReservationService;
import com.seatforge.booking.web.dto.BookingResponse;
import com.seatforge.booking.web.dto.HoldSeatRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private static final Duration DEFAULT_HOLD_DURATION = Duration.ofMinutes(10);

    private final SeatReservationService reservationService;

    public BookingController(SeatReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/holds")
    public ResponseEntity<BookingResponse> holdSeat(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody HoldSeatRequest request
    ) {
        Booking booking = reservationService.holdSeat(
                request.seatId(),
                user.userId().toString(),
                DEFAULT_HOLD_DURATION);
        BookingResponse response = toResponse(booking);
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + booking.getId()))
                .body(response);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<BookingResponse> confirm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        Booking booking = reservationService.confirmBooking(id, user.userId());
        return ResponseEntity.ok(toResponse(booking));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        reservationService.cancelBooking(id, user.userId());
        return ResponseEntity.noContent().build();
    }

    private BookingResponse toResponse(Booking b) {
        return new BookingResponse(
                b.getId(), b.getSeat().getId(), b.getEvent().getId(),
                b.getUserId(), b.getStatus().name(),
                b.getHoldExpiresAt(), b.getCreatedAt()
        );
    }
}