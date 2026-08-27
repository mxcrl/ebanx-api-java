package com.ebanx.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A single account. Balance is an AtomicLong rather than a plain long
 * so that concurrent requests touching the same account (the HTTP
 * server dispatches each request on its own virtual thread) can't
 * race and silently drop an update.
 */
public final class Account {

    private final String id;
    private final AtomicLong balance;

    public Account(String id, long initialBalance) {
        this.id = id;
        this.balance = new AtomicLong(initialBalance);
    }

    public String getId() {
        return id;
    }

    public long getBalance() {
        return balance.get();
    }

    public long deposit(long amount) {
        return balance.addAndGet(amount);
    }

    /**
     * Withdrawals are allowed to push the balance negative - that is a
     * deliberate business rule for this exercise, not a bug.
     */
    public long withdraw(long amount) {
        return balance.addAndGet(-amount);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", id);
        json.put("balance", getBalance());
        return json;
    }
}
