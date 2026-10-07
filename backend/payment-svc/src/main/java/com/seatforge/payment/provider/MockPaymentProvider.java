package com.seatforge.payment.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(
        name = "seatforge.payment.provider",
        havingValue = "mock",
        matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentProvider.class);

    // Special amount that always fails authorization, for testing
    static final long FAIL_AMOUNT = 999L;

    // Tracks which authorization IDs have been captured or voided
    private final Map<String, String> captured = new ConcurrentHashMap<>();

    @Override
    public PaymentResult authorize(long bookingId, String userId, long amountCents) {
        if (amountCents <= 0) {
            return PaymentResult.failure("invalid amount: " + amountCents);
        }
        if (amountCents == FAIL_AMOUNT) {
            return PaymentResult.failure("mock provider declined amount " + amountCents);
        }
        String ref = "mock_auth_" + UUID.randomUUID();
        log.info("Mock authorize booking={} user={} amount={} ref={}",
                bookingId, userId, amountCents, ref);
        return PaymentResult.success(ref);
    }

    @Override
    public PaymentResult capture(String authorizationId) {
        if (captured.containsKey(authorizationId)) {
            return PaymentResult.failure("already captured: " + authorizationId);
        }
        captured.put(authorizationId, "CAPTURED");
        String ref = "mock_capture_" + UUID.randomUUID();
        log.info("Mock capture auth={} ref={}", authorizationId, ref);
        return PaymentResult.success(ref);
    }

    @Override
    public PaymentResult voidAuthorization(String authorizationId) {
        captured.put(authorizationId, "VOIDED");
        String ref = "mock_void_" + UUID.randomUUID();
        log.info("Mock void auth={} ref={}", authorizationId, ref);
        return PaymentResult.success(ref);
    }
}