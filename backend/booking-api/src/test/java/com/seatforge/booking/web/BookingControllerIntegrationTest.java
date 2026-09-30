package com.seatforge.booking.web;

import com.seatforge.booking.TestcontainersConfiguration;
import com.seatforge.booking.domain.Event;
import com.seatforge.booking.domain.Seat;
import com.seatforge.booking.repository.EventRepository;
import com.seatforge.booking.repository.SeatRepository;
import com.seatforge.booking.web.dto.BookingResponse;
import com.seatforge.booking.web.dto.HoldSeatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class BookingControllerIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;

    private Long seatId;

    @BeforeEach
    void setUp() {
        Event event = Event.create("Test Concert", "Test Arena", Instant.now().plus(Duration.ofDays(30)));
        event = eventRepository.save(event);
        Seat seat = Seat.create(event, "A", "1", 1, 5000);
        seat = seatRepository.save(seat);
        seatId = seat.getId();
    }

    @Test
    void holdConfirmCancelLifecycle() {
        // Hold
        ResponseEntity<BookingResponse> holdResp = restTemplate.postForEntity(
                "/api/v1/bookings/holds",
                new HoldSeatRequest(seatId, "user-1"),
                BookingResponse.class);
        assertThat(holdResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(holdResp.getBody()).isNotNull();
        assertThat(holdResp.getBody().status()).isEqualTo("HELD");
        Long bookingId = holdResp.getBody().id();

        // Confirm
        ResponseEntity<BookingResponse> confirmResp = restTemplate.postForEntity(
                "/api/v1/bookings/" + bookingId + "/confirm", null, BookingResponse.class);
        assertThat(confirmResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmResp.getBody().status()).isEqualTo("CONFIRMED");

        // Second hold attempt should fail (seat is CONFIRMED)
        ResponseEntity<String> secondHold = restTemplate.postForEntity(
                "/api/v1/bookings/holds",
                new HoldSeatRequest(seatId, "user-2"),
                String.class);
        assertThat(secondHold.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Cancel
        restTemplate.delete("/api/v1/bookings/" + bookingId);

        // Verify: seat is now holdable again (cancellation freed it)
        ResponseEntity<BookingResponse> rehold = restTemplate.postForEntity(
                "/api/v1/bookings/holds",
                new HoldSeatRequest(seatId, "user-3"),
                BookingResponse.class);
        assertThat(rehold.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(rehold.getBody()).isNotNull();
        assertThat(rehold.getBody().status()).isEqualTo("HELD");

    }

    @Test
    void holdWithInvalidRequestReturns400() {
        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/v1/bookings/holds",
                new HoldSeatRequest(null, "user-1"),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}