package com.ebanx.domain;

import java.util.Map;

/**
 * Everything about "is this request well-formed" lives here, in one
 * place, instead of being scattered across the HTTP handler. Nothing
 * in this class knows about status codes - it either returns a valid
 * Event or throws MalformedEventException.
 */
public final class EventParser {

    private EventParser() {
    }

    public static Event parse(Map<String, Object> json) {
        if (json == null) {
            throw new MalformedEventException("Request body must be a JSON object");
        }

        String type = requireString(json, "type");
        long amount = requireAmount(json);

        return switch (type) {
            case "deposit" -> new Deposit(requireString(json, "destination"), amount);
            case "withdraw" -> new Withdraw(requireString(json, "origin"), amount);
            case "transfer" -> new Transfer(
                    requireString(json, "origin"),
                    requireString(json, "destination"),
                    amount);
            default -> throw new MalformedEventException("Unknown event type: \"" + type + "\"");
        };
    }

    private static String requireString(Map<String, Object> json, String field) {
        Object value = json.get(field);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new MalformedEventException("Missing or invalid \"" + field + "\" field");
        }
        return stringValue;
    }

    private static long requireAmount(Map<String, Object> json) {
        Object value = json.get("amount");
        if (!(value instanceof Number number)) {
            throw new MalformedEventException("Missing or invalid \"amount\" field");
        }
        double asDouble = number.doubleValue();
        if (Double.isNaN(asDouble) || Double.isInfinite(asDouble) || asDouble != Math.rint(asDouble)) {
            throw new MalformedEventException("\"amount\" must be a whole number");
        }
        return (long) asDouble;
    }
}
