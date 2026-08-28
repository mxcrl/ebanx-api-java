package com.ebanx.web;

import com.ebanx.domain.AccountNotFoundException;
import com.ebanx.domain.MalformedEventException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps domain exceptions (and request-parsing failures) to HTTP
 * responses in one place, so the controller stays free of status-code
 * decisions. Anything not explicitly handled here becomes a 500 -
 * an unforeseen bug should never crash the server or hang the client.
 */
@RestControllerAdvice
public final class GlobalExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<String> handleAccountNotFound(AccountNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_PLAIN)
                .body("0");
    }

    @ExceptionHandler({MalformedEventException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(exception.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        return ResponseEntity.internalServerError().body(Map.of("error", "Internal Server Error"));
    }
}
