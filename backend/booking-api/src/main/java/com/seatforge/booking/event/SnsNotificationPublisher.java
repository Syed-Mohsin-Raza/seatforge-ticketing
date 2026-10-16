package com.seatforge.booking.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Component
@ConditionalOnProperty(
        name = "seatforge.events.publisher",
        havingValue = "sns",
        matchIfMissing = true)
public class SnsNotificationPublisher implements NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(SnsNotificationPublisher.class);

    private final SnsClient snsClient;
    private final JsonMapper jsonMapper;
    private final String topicArn;

    public SnsNotificationPublisher(
            SnsClient snsClient,
            JsonMapper jsonMapper,
            @Value("${seatforge.events.topic-arn}") String topicArn) {
        this.snsClient = snsClient;
        this.jsonMapper = jsonMapper;
        this.topicArn = topicArn;
    }

    @Override
    public void publish(BookingEvent event) {
        try {
            String payload = jsonMapper.writeValueAsString(event);

            PublishRequest request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(payload)
                    .messageAttributes(Map.of(
                            "eventType", MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(event.eventType().name())
                                    .build()))
                    .build();

            var response = snsClient.publish(request);
            log.info("Published {} event {} to SNS (messageId={})",
                    event.eventType(), event.eventId(), response.messageId());
        } catch (Exception e) {
            log.error("Failed to publish {} event {}: {}",
                    event.eventType(), event.eventId(), e.getMessage(), e);
            // Do not rethrow. Booking state is already committed.
        }
    }
}