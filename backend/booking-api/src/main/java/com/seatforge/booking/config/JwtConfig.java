package com.seatforge.booking.config;

import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    /**
     * HS256 requires a key of at least 256 bits (32 bytes).
     * See RFC 7518, Section 3.2.
     */
    private static final int MIN_KEY_BYTES = 32;

    @Bean
    public SecretKey jwtSigningKey(
            @Value("${seatforge.jwt.secret}") String secret) {

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "seatforge.jwt.secret is not set. "
                            + "Set SEATFORGE_JWT_SECRET to a random value of at least 32 bytes.");
        }

        // The dev fallback is intentionally invalid so production cannot run on it.
        if (secret.equals("dev-secret-change-me")) {
            throw new IllegalStateException(
                    "The default JWT secret 'dev-secret-change-me' is not allowed. "
                            + "Set SEATFORGE_JWT_SECRET to a random value of at least 32 bytes. "
                            + "Generate one with: openssl rand -base64 48");
        }

        // Interpret the secret as a Base64-encoded byte array, which is the
        // conventional way to store a random HMAC key as a string.
        // If the string is not valid Base64, fall back to raw UTF-8 bytes
        // and hash them to 32 bytes. This is defensive: a well-configured
        // deployment always provides a Base64-encoded random key.
        byte[] keyBytes;
        try {
            keyBytes = io.jsonwebtoken.io.Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            log.warn("JWT secret is not valid Base64; deriving a 32-byte key via SHA-256. "
                    + "Generate a proper Base64 key with: openssl rand -base64 48");
            keyBytes = deriveKey(secret);
        }

        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(String.format(
                    "JWT secret is too short: %d bytes. HS256 requires at least %d bytes. "
                            + "Generate a valid secret with: openssl rand -base64 48",
                    keyBytes.length, MIN_KEY_BYTES));
        }

        try {
            SecretKey key = Keys.hmacShaKeyFor(keyBytes);
            log.info("JWT signing key initialized successfully ({} bytes)", keyBytes.length);
            return key;
        } catch (WeakKeyException e) {
            throw new IllegalStateException(
                    "JWT secret is rejected by JJWT: " + e.getMessage(), e);
        }
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private byte[] deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}