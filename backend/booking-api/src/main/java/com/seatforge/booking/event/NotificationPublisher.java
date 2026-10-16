package com.seatforge.booking.event;

/**
 * Publishes booking lifecycle events to a message broker.

 * Implementations must:
 *  - not throw on transient failures (log and swallow)
 *  - be idempotent-safe (callers may retry)
 *  - attach the eventType as a message attribute for filtering
 */
public interface NotificationPublisher {

    void publish(BookingEvent event);
}