package com.seatforge.payment.grpc;

import com.seatforge.payment.domain.Payment;
import com.seatforge.payment.service.PaymentService;
import com.seatforge.payment.v1.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(PaymentGrpcService.class);

    private final PaymentService paymentService;

    public PaymentGrpcService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public void authorize(AuthorizeRequest req, StreamObserver<AuthorizeResponse> observer) {
        log.info("gRPC authorize booking={} user={} amount={} key={}",
                req.getBookingId(), req.getUserId(), req.getAmountCents(), req.getIdempotencyKey());
        try {
            Payment payment = paymentService.authorize(
                    req.getBookingId(),
                    req.getUserId(),
                    req.getAmountCents(),
                    req.getIdempotencyKey());

            AuthorizeResponse resp = AuthorizeResponse.newBuilder()
                    .setAuthorizationId(payment.getAuthorizationId())
                    .setStatus(payment.getStatus().name())
                    .setProviderReference(payment.getProviderReference() == null
                            ? "" : payment.getProviderReference())
                    .build();
            observer.onNext(resp);
            observer.onCompleted();
        } catch (Exception e) {
            log.warn("authorize failed: {}", e.getMessage());
            Status status = GrpcErrorMapper.mapError(e);
            observer.onError(status.asRuntimeException());
        }
    }

    @Override
    public void capture(CaptureRequest req, StreamObserver<CaptureResponse> observer) {
        log.info("gRPC capture auth={}", req.getAuthorizationId());
        try {
            Payment payment = paymentService.capture(req.getAuthorizationId());
            CaptureResponse resp = CaptureResponse.newBuilder()
                    .setStatus(payment.getStatus().name())
                    .build();
            observer.onNext(resp);
            observer.onCompleted();
        } catch (Exception e) {
            log.warn("capture failed: {}", e.getMessage());
            Status status = GrpcErrorMapper.mapError(e);
            observer.onError(status.asRuntimeException());
        }
    }

    @Override
    public void void_(VoidRequest req, StreamObserver<VoidResponse> observer) {
        log.info("gRPC void auth={}", req.getAuthorizationId());
        try {
            Payment payment = paymentService.voidPayment(req.getAuthorizationId());
            VoidResponse resp = VoidResponse.newBuilder()
                    .setStatus(payment.getStatus().name())
                    .build();
            observer.onNext(resp);
            observer.onCompleted();
        } catch (Exception e) {
            log.warn("void failed: {}", e.getMessage());
            Status status = GrpcErrorMapper.mapError(e);
            observer.onError(status.asRuntimeException());
        }
    }
}