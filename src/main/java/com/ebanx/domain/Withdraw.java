package com.ebanx.domain;

public record Withdraw(String origin, long amount) implements Event {
}
