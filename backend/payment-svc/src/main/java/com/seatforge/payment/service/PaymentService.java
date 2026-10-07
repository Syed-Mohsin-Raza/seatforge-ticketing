package com.seatforge.payment.service;

import com.seatforge.payment.domain.Payment;
import com.seatforge.payment.domain.PaymentStatus;
import com.seatforge.payment.provider.PaymentProvider;
import com.seatforge.payment.provider.PaymentResult;
import com.seatforge.payment.repository.PaymentRepository;
import com.seatforge.payment.service.exceptions.PaymentFailedException;
import com.seatforge.payment.service.exceptions.PaymentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentProvider provider;

    public PaymentService(PaymentRepository paymentRepository, PaymentProvider provider) {
        this.paymentRepository = paymentRepository;
        this.provider = provider;
    }

    @Transactional
    public Payment authorize(long bookingId, String userId, long amountCents, String idempotencyKey) {
        // Fast path: check for existing payment with this idempotency key
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Authorize idempotent hit for key={}", idempotencyKey);
            return existing.get();
        }

        // Call provider
        PaymentResult result = provider.authorize(bookingId, userId, amountCents);
        if (!result.success()) {
            throw new PaymentFailedException(result.errorMessage());
        }

        // Persist
        String authorizationId = "auth_" + UUID.randomUUID();
        Payment payment = Payment.authorize(
                bookingId, userId, amountCents,
                authorizationId, result.providerReference(), idempotencyKey);

        try {
            return paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert with the same idempotency key lost the race.
            // Re-query and return the winner.
            log.warn("Concurrent authorize with same idempotency key={} lost race", idempotencyKey);
            return paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Payment not found after idempotency conflict: " + idempotencyKey));
        }
    }

    @Transactional
    public Payment capture(String authorizationId) {
        Payment payment = paymentRepository.findByAuthorizationId(authorizationId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + authorizationId));

        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            log.info("Capture idempotent hit for auth={}", authorizationId);
            return payment;
        }

        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Cannot capture payment in status " + payment.getStatus());
        }

        PaymentResult result = provider.capture(authorizationId);
        if (!result.success()) {
            payment.markCaptureFailed();
            paymentRepository.save(payment);
            throw new PaymentFailedException("Capture failed: " + result.errorMessage());
        }

        payment.capture();
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment voidPayment(String authorizationId) {
        Payment payment = paymentRepository.findByAuthorizationId(authorizationId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + authorizationId));

        if (payment.getStatus() == PaymentStatus.VOIDED) {
            return payment;
        }

        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Cannot void payment in status " + payment.getStatus());
        }

        PaymentResult result = provider.voidAuthorization(authorizationId);
        if (!result.success()) {
            throw new PaymentFailedException("Void failed: " + result.errorMessage());
        }

        payment.voidPayment();
        return paymentRepository.save(payment);
    }
}