package com.seatforge.booking.web.dto;

public record SeatResponse(
        Long id,
        Long eventId,
        String section,
        String rowLabel,
        Integer seatNumber,
        Integer priceCents,
        String status
) {}