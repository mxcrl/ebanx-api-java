package com.ebanx.repository;

import com.ebanx.model.Account;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory storage for accounts. ConcurrentHashMap (rather than a
 * plain HashMap behind a lock) so lookups don't block each other, and
 * findOrCreate uses computeIfAbsent so two simultaneous first-deposits
 * to the same new account id can't both "win" and create two Accounts.
 *
 * Durability is explicitly not required for this exercise; if it ever
 * were, this is the only class that would need to change - everything
 * above it depends only on find / findOrCreate / reset.
 */
@Repository
public final class AccountRepository {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public Account find(String id) {
        return accounts.get(id);
    }

    public Account findOrCreate(String id) {
        return accounts.computeIfAbsent(id, key -> new Account(key, 0L));
    }

    public void reset() {
        accounts.clear();
    }
}
