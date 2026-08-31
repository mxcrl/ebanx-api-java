package com.ebanx.repository;

import com.ebanx.model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountRepositoryTest {

    private AccountRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AccountRepository();
    }

    @Test
    void findReturnsNullForUnknownAccount() {
        assertThat(repository.find("missing")).isNull();
    }

    @Test
    void findOrCreateCreatesOnceAndThenReturnsTheSameInstance() {
        Account first = repository.findOrCreate("1");
        Account second = repository.findOrCreate("1");

        assertThat(first).isSameAs(second);
        assertThat(first.getBalance()).isZero();
        assertThat(repository.find("1")).isSameAs(first);
    }

    @Test
    void resetClearsEverything() {
        repository.findOrCreate("1").deposit(10L);
        repository.findOrCreate("2").deposit(20L);

        repository.reset();

        assertThat(repository.find("1")).isNull();
        assertThat(repository.find("2")).isNull();
    }
}
