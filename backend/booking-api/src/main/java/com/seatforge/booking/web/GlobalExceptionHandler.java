package com.seatforge.booking.web;

import com.seatforge.booking.service.exceptions.PaymentRequiredException;
import com.seatforge.booking.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorResponse(
                400, "Bad Request", "Validation failed", fieldErrors, Instant.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                404, "Not Found", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                409, "Conflict", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(PaymentRequiredException.class)
    public ResponseEntity<ErrorResponse> handlePaymentRequired(PaymentRequiredException ex) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(new ErrorResponse(
                402, "Payment Required", ex.getMessage(), null, Instant.now()));
    }
}