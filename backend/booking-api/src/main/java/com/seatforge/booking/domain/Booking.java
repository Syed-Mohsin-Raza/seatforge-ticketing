package com.seatforge.booking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "payment_authorization_id", length = 64)
    private String paymentAuthorizationId;

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

    // Factory: no public constructor, forces explicit intent
    public static Booking hold(Event event, Seat seat, String userId, Instant expiresAt) {
        Booking b = new Booking();
        b.event = event;
        b.seat = seat;
        b.userId = userId;
        b.status = BookingStatus.HELD;
        b.holdExpiresAt = expiresAt;
        return b;
    }

    public void confirm() {
        if (this.status != BookingStatus.HELD) {
            throw new IllegalStateException("Only HELD bookings can be confirmed");
        }
        if (this.holdExpiresAt != null && Instant.now().isAfter(this.holdExpiresAt)) {
            throw new IllegalStateException("Hold has expired");
        }
        this.status = BookingStatus.CONFIRMED;
        this.holdExpiresAt = null;
    }

    public void cancel() {
        if (this.status == BookingStatus.CANCELLED || this.status == BookingStatus.EXPIRED) {
            throw new IllegalStateException("Booking cannot be cancelled from status: " + this.status);
        }
        this.status = BookingStatus.CANCELLED;
    }
}