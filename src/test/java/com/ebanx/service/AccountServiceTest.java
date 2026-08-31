package com.ebanx.service;

import com.ebanx.dto.Deposit;
import com.ebanx.dto.Transfer;
import com.ebanx.dto.Withdraw;
import com.ebanx.exception.AccountNotFoundException;
import com.ebanx.model.Account;
import com.ebanx.repository.AccountRepository;
import com.ebanx.service.AccountService.EventResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Direct unit tests for the domain layer, wired to a real in-memory
 * repository (it has no external dependencies to mock).
 */
class AccountServiceTest {

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(new AccountRepository());
    }

    @Test
    void getBalanceIsNullForUnknownAccount() {
        assertThat(service.getBalance("missing")).isNull();
    }

    @Test
    void depositCreatesAccountAndAccumulates() {
        service.deposit("100", 10L);
        Account account = service.deposit("100", 5L);

        assertThat(account.getBalance()).isEqualTo(15L);
        assertThat(service.getBalance("100")).isEqualTo(15L);
    }

    @Test
    void withdrawFromUnknownAccountThrows() {
        assertThatThrownBy(() -> service.withdraw("nope", 1L))
                .isInstanceOf(AccountNotFoundException.class)
                .satisfies(e -> assertThat(((AccountNotFoundException) e).getAccountId()).isEqualTo("nope"));
    }

    @Test
    void withdrawDebitsAndMayGoNegative() {
        service.deposit("100", 5L);
        Account account = service.withdraw("100", 20L);
        assertThat(account.getBalance()).isEqualTo(-15L);
    }

    @Test
    void transferMovesFundsBetweenAccounts() {
        service.deposit("100", 15L);
        EventResult result = service.transfer("100", "300", 15L);

        assertThat(result.origin().getBalance()).isZero();
        assertThat(result.destination().getBalance()).isEqualTo(15L);
    }

    @Test
    void transferFromUnknownOriginThrowsAndDoesNotCreateDestination() {
        assertThatThrownBy(() -> service.transfer("missing", "300", 5L))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(service.getBalance("300")).isNull();
    }

    @Test
    void resetWipesState() {
        service.deposit("100", 10L);
        service.reset();
        assertThat(service.getBalance("100")).isNull();
    }

    @Test
    void applyDispatchesDeposit() {
        EventResult result = service.apply(new Deposit("100", 10L));
        assertThat(result.origin()).isNull();
        assertThat(result.destination().getBalance()).isEqualTo(10L);
        assertThat(result.toJson()).isEqualTo(Map.of("destination", Map.of("id", "100", "balance", 10L)));
    }

    @Test
    void applyDispatchesWithdraw() {
        service.deposit("100", 10L);
        EventResult result = service.apply(new Withdraw("100", 4L));
        assertThat(result.destination()).isNull();
        assertThat(result.origin().getBalance()).isEqualTo(6L);
        assertThat(result.toJson()).containsOnlyKeys("origin");
    }

    @Test
    void applyDispatchesTransfer() {
        service.deposit("100", 10L);
        EventResult result = service.apply(new Transfer("100", "200", 4L));
        assertThat(result.toJson()).containsOnlyKeys("origin", "destination");
    }
}
