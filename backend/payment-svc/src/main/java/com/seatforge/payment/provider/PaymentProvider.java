package com.seatforge.payment.provider;

public interface PaymentProvider {

    /**
     * Authorize a payment. Reserves funds without capturing.
     * Idempotency is handled by the caller via the idempotency key.
     * The provider itself may or may not be idempotent.
     */
    PaymentResult authorize(long bookingId, String userId, long amountCents);

    /**
     * Capture an authorized payment. Moves funds from reserved to captured.
     */
    PaymentResult capture(String authorizationId);

    /**
     * Void an authorized payment. Releases reserved funds without capture.
     */
    PaymentResult voidAuthorization(String authorizationId);
}