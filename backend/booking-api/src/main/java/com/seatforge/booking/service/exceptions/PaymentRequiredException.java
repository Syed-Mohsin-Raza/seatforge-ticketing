package com.seatforge.booking.service.exceptions;

public class PaymentRequiredException extends RuntimeException {

    public PaymentRequiredException(String message) {
        super(message);
    }

    public PaymentRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
