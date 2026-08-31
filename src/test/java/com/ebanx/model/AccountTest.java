package com.ebanx.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTest {

    @Test
    void startsAtItsInitialBalance() {
        assertThat(new Account("1", 42L).getBalance()).isEqualTo(42L);
    }

    @Test
    void depositAndWithdrawReturnTheRunningBalance() {
        Account account = new Account("1", 0L);
        assertThat(account.deposit(10L)).isEqualTo(10L);
        assertThat(account.withdraw(3L)).isEqualTo(7L);
        assertThat(account.getBalance()).isEqualTo(7L);
    }

    @Test
    void withdrawIsAllowedToGoNegative() {
        Account account = new Account("1", 5L);
        assertThat(account.withdraw(20L)).isEqualTo(-15L);
    }

    @Test
    void toJsonExposesIdAndBalanceInOrder() {
        Account account = new Account("acc-1", 5L);
        account.deposit(5L);
        assertThat(account.toJson())
                .containsExactly(
                        org.assertj.core.api.Assertions.entry("id", "acc-1"),
                        org.assertj.core.api.Assertions.entry("balance", 10L));
        assertThat(account.getId()).isEqualTo("acc-1");
    }
}
