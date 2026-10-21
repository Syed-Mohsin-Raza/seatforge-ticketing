package com.seatforge.booking.web;

import com.seatforge.booking.domain.User;
import com.seatforge.booking.security.JwtService;
import com.seatforge.booking.service.UserService;
import com.seatforge.booking.web.dto.AuthResponse;
import com.seatforge.booking.web.dto.LoginRequest;
import com.seatforge.booking.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        User user = userService.register(req.email(), req.password());
        return ResponseEntity.ok(issueToken(user));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        User user = userService.authenticate(req.email(), req.password());
        return ResponseEntity.ok(issueToken(user));
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generate(user.getPublicId(), user.getEmail());
        return new AuthResponse(token, jwtService.expiresAt(), user.getPublicId(), user.getEmail());
    }
}