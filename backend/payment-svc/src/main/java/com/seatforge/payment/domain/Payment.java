package com.seatforge.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "payments", schema = "payment")
@Getter
@Setter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "authorization_id", nullable = false, unique = true, length = 64)
    private String authorizationId;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (version == null) version = 0L;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public static Payment authorize(Long bookingId,
                                    String userId,
                                    Long amountCents,
                                    String authorizationId,
                                    String providerReference,
                                    String idempotencyKey) {
        Payment p = new Payment();
        p.bookingId = bookingId;
        p.userId = userId;
        p.amountCents = amountCents;
        p.status = PaymentStatus.AUTHORIZED;
        p.authorizationId = authorizationId;
        p.providerReference = providerReference;
        p.idempotencyKey = idempotencyKey;
        return p;
    }

    public void capture() {
        if (this.status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Cannot capture payment in status " + this.status);
        }
        this.status = PaymentStatus.CAPTURED;
    }

    public void voidPayment() {
        if (this.status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Cannot void payment in status " + this.status);
        }
        this.status = PaymentStatus.VOIDED;
    }

    public void markCaptureFailed() {
        if (this.status != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException(
                    "Cannot mark capture failed in status " + this.status);
        }
        this.status = PaymentStatus.CAPTURE_FAILED;
    }
}