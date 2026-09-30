package com.seatforge.booking.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record HoldSeatRequest(
        @NotNull @Positive Long seatId,
        @NotNull String userId
) {}