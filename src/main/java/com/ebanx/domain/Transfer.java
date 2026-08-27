package com.ebanx.domain;

public record Transfer(String origin, String destination, long amount) implements Event {
}
