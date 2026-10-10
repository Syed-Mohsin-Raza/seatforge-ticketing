package com.seatforge.booking.grpc;

import com.seatforge.payment.v1.AuthorizeRequest;
import com.seatforge.payment.v1.AuthorizeResponse;
import com.seatforge.payment.v1.CaptureRequest;
import com.seatforge.payment.v1.CaptureResponse;
import com.seatforge.payment.v1.PaymentServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class PaymentServiceClient {

    private final ManagedChannel channel;
    private final PaymentServiceGrpc.PaymentServiceBlockingStub stub;

    public PaymentServiceClient(
            @Value("${seatforge.payment-svc.address:localhost:9091}") String address) {
        String[] parts = address.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9091;

        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.stub = PaymentServiceGrpc.newBlockingStub(channel);
    }

    public AuthorizeResponse authorize(long bookingId,
                                       String userId,
                                       long amountCents,
                                       String idempotencyKey) {
        return stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .authorize(AuthorizeRequest.newBuilder()
                        .setBookingId(bookingId)
                        .setUserId(userId)
                        .setAmountCents(amountCents)
                        .setIdempotencyKey(idempotencyKey)
                        .build());
    }

    public CaptureResponse capture(String authorizationId) {
        return stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .capture(CaptureRequest.newBuilder()
                        .setAuthorizationId(authorizationId)
                        .build());
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}