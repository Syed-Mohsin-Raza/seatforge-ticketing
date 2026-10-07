package com.seatforge.payment.grpc;

import com.seatforge.payment.service.exceptions.PaymentFailedException;
import com.seatforge.payment.service.exceptions.PaymentNotFoundException;
import io.grpc.Status;

public final class GrpcErrorMapper {

    private GrpcErrorMapper() {}

    public static Status mapError(Throwable t) {
        if (t instanceof PaymentNotFoundException) {
            return Status.NOT_FOUND.withDescription(t.getMessage());
        }
        if (t instanceof PaymentFailedException) {
            return Status.FAILED_PRECONDITION.withDescription(t.getMessage());
        }
        if (t instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(t.getMessage());
        }
        if (t instanceof IllegalStateException) {
            return Status.FAILED_PRECONDITION.withDescription(t.getMessage());
        }
        return Status.INTERNAL.withDescription(t.getClass().getSimpleName() + ": " + t.getMessage());
    }
}