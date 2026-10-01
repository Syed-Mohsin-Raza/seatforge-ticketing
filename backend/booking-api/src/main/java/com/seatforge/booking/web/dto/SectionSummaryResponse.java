package com.seatforge.booking.web.dto;

public record SectionSummaryResponse(
        String section,
        long total,
        long available,
        long held,
        long confirmed
) {}