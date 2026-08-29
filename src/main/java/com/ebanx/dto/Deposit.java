package com.ebanx.dto;

public record Deposit(String destination, long amount) implements Event {
}
