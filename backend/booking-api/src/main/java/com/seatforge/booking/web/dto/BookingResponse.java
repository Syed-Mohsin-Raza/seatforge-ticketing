package com.seatforge.booking.web.dto;

import java.time.Instant;

public record BookingResponse(
        Long id,
        Long seatId,
        Long eventId,
        String userId,
        String status,
        Instant holdExpiresAt,
        Instant createdAt
) {}