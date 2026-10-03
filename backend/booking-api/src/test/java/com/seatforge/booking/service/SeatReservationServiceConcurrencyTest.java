package com.seatforge.booking.service;

import com.seatforge.booking.TestcontainersConfiguration;
import com.seatforge.booking.domain.Event;
import com.seatforge.booking.domain.Seat;
import com.seatforge.booking.repository.EventRepository;
import com.seatforge.booking.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SeatReservationServiceConcurrencyTest {

    @Autowired
    private SeatReservationService reservationService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SeatRepository seatRepository;

    private Long seatId;

    @BeforeEach
    void setUp() {
        Event event = Event.create("Test Concert", "Test Arena", Instant.now().plus(Duration.ofDays(30)));
        event = eventRepository.save(event);

        Seat seat = Seat.create(event, "A", "1", 1, 5000);
        seat = seatRepository.save(seat);
        seatId = seat.getId();
    }


    @Disabled("Requires hold-svc; concurrency is proven in hold-svc's Go tests (go test -race)")
    @Test
    void concurrentHolds_shouldOnlyAllowOneWinner() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);

        var successes = new java.util.concurrent.atomic.AtomicInteger(0);
        var failures = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final String userId = "user-" + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    reservationService.holdSeat(seatId, userId, Duration.ofMinutes(10));
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(completed).as("all threads should finish within timeout").isTrue();
        assertThat(successes.get()).as("exactly one hold should succeed").isEqualTo(1);
        assertThat(failures.get()).as("all other holds should fail").isEqualTo(threads - 1);
    }
}