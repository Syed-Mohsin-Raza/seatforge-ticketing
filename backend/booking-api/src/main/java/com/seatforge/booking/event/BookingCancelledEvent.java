package com.seatforge.booking.event;

import java.time.Instant;
import java.util.UUID;

public record BookingCancelledEvent(
        UUID eventId,
        Instant occurredAt,
        Long bookingId,
        Long seatId,
        Long domainEventId,
        String userId
) implements BookingEvent {

    @Override
    public BookingEventType eventType() {
        return BookingEventType.BOOKING_CANCELLED;
    }
}