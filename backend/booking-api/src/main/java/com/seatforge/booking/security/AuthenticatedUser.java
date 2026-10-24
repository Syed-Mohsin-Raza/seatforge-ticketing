package com.seatforge.booking.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email) {}