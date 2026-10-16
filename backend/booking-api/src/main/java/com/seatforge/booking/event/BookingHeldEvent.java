package com.seatforge.booking.event;

import java.time.Instant;
import java.util.UUID;

public record BookingHeldEvent(
        UUID eventId,
        Instant occurredAt,
        Long bookingId,
        Long seatId,
        Long domainEventId,   // the ticketing event (concert, match, etc.)
        String userId,
        Instant holdExpiresAt
) implements BookingEvent {

    @Override
    public BookingEventType eventType() {
        return BookingEventType.BOOKING_HELD;
    }
}