package com.ebanx.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All business rules for the "banking" domain live here. It knows
 * nothing about Express-style routing, status codes, or JSON parsing.
 * That separation is what keeps this layer trivial to unit test and
 * reusable if the transport ever changes.
 */
public final class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    public void reset() {
        repository.reset();
    }

    /**
     * @return the balance, or null if the account doesn't exist
     */
    public Long getBalance(String accountId) {
        Account account = repository.find(accountId);
        return account == null ? null : account.getBalance();
    }

    /**
     * Deposits create the account on the fly if it doesn't exist yet.
     */
    public Account deposit(String destinationId, long amount) {
        Account account = repository.findOrCreate(destinationId);
        account.deposit(amount);
        return account;
    }

    /**
     * Withdrawals require an existing account. Balances are allowed to
     * go negative - that's an explicit business rule, not an omission.
     *
     * @throws AccountNotFoundException if originId doesn't exist
     */
    public Account withdraw(String originId, long amount) {
        Account account = repository.find(originId);
        if (account == null) {
            throw new AccountNotFoundException(originId);
        }
        account.withdraw(amount);
        return account;
    }

    /**
     * A transfer is a withdraw from the origin followed by a deposit
     * into the destination. Reusing those two operations keeps the
     * rules (existing-origin required, negative balances allowed,
     * destination auto-created) defined in exactly one place each.
     *
     * @throws AccountNotFoundException if originId doesn't exist
     */
    public EventResult transfer(String originId, String destinationId, long amount) {
        Account origin = withdraw(originId, amount);
        Account destination = deposit(destinationId, amount);
        return new EventResult(origin, destination);
    }

    /**
     * Dispatches a validated Event to the matching operation. Event is
     * a sealed interface with exactly three permitted records, so this
     * switch is exhaustive: the compiler refuses to build if a new
     * event type is ever added here without being handled.
     *
     * @throws AccountNotFoundException if a required account is missing
     */
    public EventResult apply(Event event) {
        return switch (event) {
            case Deposit deposit -> new EventResult(null, deposit(deposit.destination(), deposit.amount()));
            case Withdraw withdraw -> new EventResult(withdraw(withdraw.origin(), withdraw.amount()), null);
            case Transfer transfer -> transfer(transfer.origin(), transfer.destination(), transfer.amount());
        };
    }

    /**
     * Result of applying an event. Exactly one of origin/destination is
     * null for deposit and withdraw; both are set for transfer.
     */
    public record EventResult(Account origin, Account destination) {

        public Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            if (origin != null) {
                json.put("origin", origin.toJson());
            }
            if (destination != null) {
                json.put("destination", destination.toJson());
            }
            return json;
        }
    }
}
