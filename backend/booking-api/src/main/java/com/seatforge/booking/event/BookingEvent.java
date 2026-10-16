package com.seatforge.booking.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker interface for all booking events published to SNS.
 * Concrete implementations are records that carry the event payload.
 *
 * Every event carries a stable {@code eventId} (UUID) that consumers
 * can use for idempotent processing.
 */
public sealed interface BookingEvent
        permits BookingHeldEvent, BookingConfirmedEvent, BookingCancelledEvent {

    UUID eventId();

    BookingEventType eventType();

    Instant occurredAt();
}