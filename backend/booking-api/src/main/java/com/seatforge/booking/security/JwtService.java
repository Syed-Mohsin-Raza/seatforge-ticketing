package com.seatforge.booking.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration ttl;

    public JwtService(
            SecretKey signingKey,
            @Value("${seatforge.jwt.ttl-minutes:60}") long ttlMinutes) {
        this.signingKey = signingKey;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String generate(UUID userId, String email) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public Instant expiresAt() {
        return Instant.now().plus(ttl);
    }

    /**
     * Parses and validates a JWT. Throws JwtException if the
     * signature is invalid, the token is malformed, or it has expired.
     */
    public Jws<Claims> parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
    }
}