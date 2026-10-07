package com.seatforge.payment.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PaymentGrpcServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PaymentGrpcServer.class);

    private final PaymentGrpcService paymentGrpcService;
    private final int port;
    private Server server;
    private volatile boolean running = false;

    public PaymentGrpcServer(
            PaymentGrpcService paymentGrpcService,
            @Value("${seatforge.payment.grpc.port:9091}") int port) {
        this.paymentGrpcService = paymentGrpcService;
        this.port = port;
    }

    @Override
    public void start() {
        try {
            this.server = ServerBuilder.forPort(port)
                    .addService(paymentGrpcService)
                    .addService(ProtoReflectionService.newInstance())
                    .build()
                    .start();
            this.running = true;
            log.info("payment-svc gRPC server listening on port {}", port);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start gRPC server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            log.info("shutting down gRPC server");
            server.shutdown();
            try {
                if (!server.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
            this.running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}