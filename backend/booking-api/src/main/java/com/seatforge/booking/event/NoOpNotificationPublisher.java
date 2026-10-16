package com.seatforge.booking.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "seatforge.events.publisher",
        havingValue = "noop")
public class NoOpNotificationPublisher implements NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpNotificationPublisher.class);

    @Override
    public void publish(BookingEvent event) {
        log.info("[noop-publisher] would publish event type={} id={}",
                event.eventType(), event.eventId());
    }
}