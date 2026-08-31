package com.ebanx.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
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

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<String> handleAccountNotFound(AccountNotFoundException exception) {
        log.info("Handled AccountNotFoundException -> 404: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_PLAIN)
                .body("0");
    }

    @ExceptionHandler({MalformedEventException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception exception) {
        log.warn("Handled bad request -> 400: {}", exception.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(exception.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        // Spring's own MVC failures (unacceptable Accept header, wrong method,
        // unsupported media type, ...) implement ErrorResponse and already
        // carry the right 4xx status. Honour it instead of masking every one
        // of them as a 500.
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            log.warn("Handled {} -> {}", exception.getClass().getSimpleName(), status.value());
            return ResponseEntity.status(status)
                    .body(Map.of("error", String.valueOf(exception.getMessage())));
        }

        log.error("Unhandled exception -> 500", exception);
        return ResponseEntity.internalServerError().body(Map.of("error", "Internal Server Error"));
    }
}
