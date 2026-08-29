package com.ebanx.dto;

public record Withdraw(String origin, long amount) implements Event {
}
