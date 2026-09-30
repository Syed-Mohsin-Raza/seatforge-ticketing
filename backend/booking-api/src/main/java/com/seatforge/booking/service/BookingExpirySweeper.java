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
import java.util.List;

@Component
public class BookingExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(BookingExpirySweeper.class);

    private final BookingRepository bookingRepository;

    public BookingExpirySweeper(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    @Transactional
    public void expireStaleHolds() {
        List<Booking> stale = bookingRepository.findByStatusAndHoldExpiresAtBefore(
                BookingStatus.HELD, Instant.now());
        if (stale.isEmpty()) return;

        for (Booking b : stale) {
            b.setStatus(BookingStatus.EXPIRED);
        }
        bookingRepository.saveAll(stale);
        log.info("Expired {} stale holds", stale.size());
    }
}