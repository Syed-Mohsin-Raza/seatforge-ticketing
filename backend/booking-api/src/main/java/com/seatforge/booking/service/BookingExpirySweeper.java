package com.seatforge.booking.service;

import com.seatforge.booking.domain.Booking;
import com.seatforge.booking.domain.BookingStatus;
import com.seatforge.booking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class BookingExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(BookingExpirySweeper.class);

    private final BookingRepository bookingRepository;
    private final SeatAvailabilityCache cache;


    public BookingExpirySweeper(BookingRepository bookingRepository, SeatAvailabilityCache cache) {
        this.bookingRepository = bookingRepository;
        this.cache = cache;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    @Transactional
    public void expireStaleHolds() {
        List<Booking> stale = bookingRepository.findByStatusAndHoldExpiresAtBefore(
                BookingStatus.HELD, Instant.now());
        if (stale.isEmpty()) return;

        Set<Long> affectedEventIds = new HashSet<>();
        for (Booking b : stale) {
            b.setStatus(BookingStatus.EXPIRED);
            affectedEventIds.add(b.getEvent().getId());
        }
        bookingRepository.saveAll(stale);
        affectedEventIds.forEach(eventId -> {
            cache.invalidate(eventId);
            cache.invalidateSummary(eventId);
        });
        log.info("Expired {} stale holds, invalidated {} event caches", stale.size(), affectedEventIds.size());
    }
}