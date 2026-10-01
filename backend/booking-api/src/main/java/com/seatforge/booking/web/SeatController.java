package com.seatforge.booking.web;

import com.seatforge.booking.domain.Booking;
import com.seatforge.booking.domain.BookingStatus;
import com.seatforge.booking.domain.Seat;
import com.seatforge.booking.repository.BookingRepository;
import com.seatforge.booking.repository.SeatRepository;
import com.seatforge.booking.service.SeatAvailabilityCache;
import com.seatforge.booking.web.dto.EventSummaryResponse;
import com.seatforge.booking.web.dto.SeatResponse;
import com.seatforge.booking.web.dto.SectionSummaryResponse;
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
            cache.recordHit();
            return ResponseEntity.ok()
                    .header("X-Cache", "HIT")
                    .body(cached.get());
        }

        cache.recordMiss();

        List<SeatResponse> responses = loadFromDb(eventId);
        if (responses.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        cache.put(eventId, responses);
        return ResponseEntity.ok()
                .header("X-Cache", "MISS")
                .body(responses);
    }

    @GetMapping("/summary")
    public ResponseEntity<EventSummaryResponse> summary(@PathVariable Long eventId) {
        Optional<EventSummaryResponse> cached = cache.getSummary(eventId);
        if (cached.isPresent()) {
            return ResponseEntity.ok()
                    .header("X-Cache", "HIT")
                    .body(cached.get());
        }

        List<Object[]> rows = seatRepository.summarizeBySection(eventId);
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        long totalAvailable = 0;
        long totalHeld = 0;
        long totalConfirmed = 0;
        long grandTotal = 0;
        List<SectionSummaryResponse> sections = new java.util.ArrayList<>(rows.size());

        for (Object[] row : rows) {
            String section = (String) row[0];
            long available = ((Number) row[1]).longValue();
            long held = ((Number) row[2]).longValue();
            long confirmed = ((Number) row[3]).longValue();
            long total = ((Number) row[4]).longValue();

            sections.add(new SectionSummaryResponse(section, total, available, held, confirmed));
            totalAvailable += available;
            totalHeld += held;
            totalConfirmed += confirmed;
            grandTotal += total;
        }

        EventSummaryResponse response = new EventSummaryResponse(
                eventId, grandTotal, totalAvailable, totalHeld, totalConfirmed, sections);

        cache.putSummary(eventId, response);
        return ResponseEntity.ok()
                .header("X-Cache", "MISS")
                .body(response);
    }

    private List<SeatResponse> loadFromDb(Long eventId) {
        List<Seat> seats = seatRepository.findByEventId(eventId);
        if (seats.isEmpty()) {
            return List.of();
        }

        List<Long> seatIds = seats.stream().map(Seat::getId).toList();

        Map<Long, String> statusBySeat = bookingRepository.findLatestActiveStatuses(seatIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((BookingStatus) row[1]).name()
                ));

        return seats.stream()
                .map(s -> new SeatResponse(
                        s.getId(),
                        s.getEvent().getId(),
                        s.getSection(),
                        s.getRowLabel(),
                        s.getSeatNumber(),
                        s.getPriceCents(),
                        statusBySeat.getOrDefault(s.getId(), "AVAILABLE")
                ))
                .toList();
    }
}