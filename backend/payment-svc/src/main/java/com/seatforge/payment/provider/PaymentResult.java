package com.seatforge.payment.provider;

public record PaymentResult(
        boolean success,
        String providerReference,
        String errorMessage
) {
    public static PaymentResult success(String providerReference) {
        return new PaymentResult(true, providerReference, null);
    }

    public static PaymentResult failure(String errorMessage) {
        return new PaymentResult(false, null, errorMessage);
    }
}