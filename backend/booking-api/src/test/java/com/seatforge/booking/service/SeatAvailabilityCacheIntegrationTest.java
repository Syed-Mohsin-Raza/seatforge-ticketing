package com.seatforge.booking.service;

import com.seatforge.booking.TestcontainersConfiguration;
import com.seatforge.booking.domain.Event;
import com.seatforge.booking.domain.Seat;
import com.seatforge.booking.repository.EventRepository;
import com.seatforge.booking.repository.SeatRepository;
import com.seatforge.booking.web.dto.SeatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class SeatAvailabilityCacheIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired SeatAvailabilityCache cache;

    private Long eventId;
    private Long seatId;

    @BeforeEach
    void setUp() {
        Event event = Event.create("Cache Test", "Venue", Instant.now().plus(Duration.ofDays(30)));
        event = eventRepository.save(event);
        eventId = event.getId();

        Seat seat = Seat.create(event, "A", "1", 1, 5000);
        seat = seatRepository.save(seat);
        seatId = seat.getId();

        cache.invalidate(eventId);
    }

    @Test
    void firstReadIsCacheMissSecondIsHit() {
        ResponseEntity<String> first = restTemplate.getForEntity(
                "/api/v1/events/" + eventId + "/seats", String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getHeaders().getFirst("X-Cache")).isEqualTo("MISS");

        ResponseEntity<String> second = restTemplate.getForEntity(
                "/api/v1/events/" + eventId + "/seats", String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getHeaders().getFirst("X-Cache")).isEqualTo("HIT");
    }

    @Test
    void holdInvalidatesCache() {
        // Populate cache
        restTemplate.getForEntity("/api/v1/events/" + eventId + "/seats", String.class);

        // Hold seat
        restTemplate.postForEntity(
                "/api/v1/bookings/holds",
                new com.seatforge.booking.web.dto.HoldSeatRequest(seatId, "user-1"),
                String.class);

        // Next read should be a MISS (cache was invalidated)
        ResponseEntity<String> after = restTemplate.getForEntity(
                "/api/v1/events/" + eventId + "/seats", String.class);
        assertThat(after.getHeaders().getFirst("X-Cache")).isEqualTo("MISS");
    }
}