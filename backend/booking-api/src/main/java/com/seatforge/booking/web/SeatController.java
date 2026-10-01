package com.seatforge.booking.web;

import com.seatforge.booking.domain.Booking;
import com.seatforge.booking.domain.BookingStatus;
import com.seatforge.booking.domain.Seat;
import com.seatforge.booking.repository.BookingRepository;
import com.seatforge.booking.repository.SeatRepository;
import com.seatforge.booking.service.SeatAvailabilityCache;
import com.seatforge.booking.web.dto.SeatResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/events/{eventId}/seats")
public class SeatController {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final SeatAvailabilityCache cache;

    public SeatController(SeatRepository seatRepository,
                          BookingRepository bookingRepository,
                          SeatAvailabilityCache cache) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.cache = cache;
    }

    @GetMapping
    public ResponseEntity<List<SeatResponse>> list(@PathVariable Long eventId) {
        Optional<List<SeatResponse>> cached = cache.get(eventId);
        if (cached.isPresent()) {
            return ResponseEntity.ok()
                    .header("X-Cache", "HIT")
                    .body(cached.get());
        }

        List<Seat> seats = seatRepository.findAll().stream()
                .filter(s -> s.getEvent().getId().equals(eventId))
                .toList();

        if (seats.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<SeatResponse> responses = seats.stream()
                .map(this::toResponse)
                .toList();

        cache.put(eventId, responses);
        return ResponseEntity.ok()
                .header("X-Cache", "MISS")
                .body(responses);
    }

    private SeatResponse toResponse(Seat seat) {
        Optional<Booking> confirmed = bookingRepository.findBySeatIdAndStatus(
                seat.getId(), BookingStatus.CONFIRMED);
        Optional<Booking> held = bookingRepository.findBySeatIdAndStatus(
                seat.getId(), BookingStatus.HELD);

        String status = confirmed.isPresent() ? "CONFIRMED"
                : held.isPresent() ? "HELD" : "AVAILABLE";

        return new SeatResponse(
                seat.getId(),
                seat.getEvent().getId(),
                seat.getSection(),
                seat.getRowLabel(),
                seat.getSeatNumber(),
                seat.getPriceCents(),
                status
        );
    }
}