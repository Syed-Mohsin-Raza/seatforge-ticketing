package com.seatforge.booking.grpc;

import com.seatforge.hold.v1.HoldServiceGrpc;
import com.seatforge.hold.v1.ReserveSeatRequest;
import com.seatforge.hold.v1.ReserveSeatResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class HoldServiceClient {

    private final ManagedChannel channel;
    private final HoldServiceGrpc.HoldServiceBlockingStub stub;

    public HoldServiceClient(
            @Value("${seatforge.hold-svc.address:localhost:9090}")
            String address) {
        String hostPort = address.replace("static://", "").replace("dns:///", "");
        String[] parts = hostPort.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9090;

        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.stub = HoldServiceGrpc.newBlockingStub(channel);
    }

    public ReserveSeatResponse reserveSeat(long seatId, String userId, Duration duration) {
        return stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .reserveSeat(ReserveSeatRequest.newBuilder()
                        .setSeatId(seatId)
                        .setUserId(userId)
                        .setHoldDurationSeconds((int) duration.toSeconds())
                        .build());
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}