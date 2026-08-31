package com.ebanx.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void accountNotFoundBecomes404WithBodyZero() {
        ResponseEntity<String> response = handler.handleAccountNotFound(new AccountNotFoundException("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo("0");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
    }

    @Test
    void malformedEventBecomes400WithErrorMessage() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleBadRequest(new MalformedEventException("bad amount"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "bad amount");
    }

    @Test
    @SuppressWarnings("deprecation")
    void unreadableJsonBecomes400() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleBadRequest(new HttpMessageNotReadableException("nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    void anythingElseBecomes500WithoutLeakingDetail() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleUnexpected(new IllegalStateException("secret internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Internal Server Error");
        assertThat(response.getBody().toString()).doesNotContain("secret internal detail");
    }
}
