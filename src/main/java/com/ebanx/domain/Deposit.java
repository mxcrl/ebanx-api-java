package com.ebanx.domain;

public record Deposit(String destination, long amount) implements Event {
}
