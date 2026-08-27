package com.ebanx.domain;

/**
 * Thrown when the request body doesn't describe a valid event: an
 * unknown/missing "type", a missing required field, or an "amount"
 * that isn't a whole number. The HTTP layer maps this to a 400.
 */
public final class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message) {
        super(message);
    }
}
