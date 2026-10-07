package com.seatforge.payment.repository;

import com.seatforge.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByAuthorizationId(String authorizationId);

    Optional<Payment> findByBookingId(Long bookingId);
}