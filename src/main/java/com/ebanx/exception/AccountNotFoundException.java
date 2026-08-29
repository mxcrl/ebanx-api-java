package com.ebanx.exception;

/**
 * Thrown when an operation requires an account that doesn't exist
 * (withdraw, or transfer with a missing origin). The HTTP layer
 * catches this and maps it to a 404 - the domain layer itself has no
 * notion of status codes.
 */
public final class AccountNotFoundException extends RuntimeException {

    private final String accountId;

    public AccountNotFoundException(String accountId) {
        super("Account \"" + accountId + "\" not found");
        this.accountId = accountId;
    }

    public String getAccountId() {
        return accountId;
    }
}
