package com.ebanx.dto;

public record Transfer(String origin, String destination, long amount) implements Event {
}
