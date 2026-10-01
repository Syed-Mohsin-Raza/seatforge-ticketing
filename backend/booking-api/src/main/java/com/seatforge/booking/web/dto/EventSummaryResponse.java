package com.seatforge.booking.web.dto;

import java.util.List;

public record EventSummaryResponse(
        Long eventId,
        long totalSeats,
        long availableSeats,
        long heldSeats,
        long confirmedSeats,
        List<SectionSummaryResponse> sections
) {}