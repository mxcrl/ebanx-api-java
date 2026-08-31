package com.ebanx.dto;

import com.ebanx.exception.MalformedEventException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for every branch of {@link EventParser} - the single
 * place where "is this request well-formed" is decided.
 */
class EventParserTest {

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], pairs[i + 1]);
        }
        return m;
    }

    @Test
    void parsesDeposit() {
        Event event = EventParser.parse(map("type", "deposit", "destination", "100", "amount", 10));
        assertThat(event).isEqualTo(new Deposit("100", 10L));
    }

    @Test
    void parsesWithdraw() {
        Event event = EventParser.parse(map("type", "withdraw", "origin", "100", "amount", 5));
        assertThat(event).isEqualTo(new Withdraw("100", 5L));
    }

    @Test
    void parsesTransfer() {
        Event event = EventParser.parse(map("type", "transfer", "origin", "1", "destination", "2", "amount", 7));
        assertThat(event).isEqualTo(new Transfer("1", "2", 7L));
    }

    @Test
    void acceptsNegativeAndZeroAmounts() {
        assertThat(EventParser.parse(map("type", "deposit", "destination", "1", "amount", -3)))
                .isEqualTo(new Deposit("1", -3L));
        assertThat(EventParser.parse(map("type", "deposit", "destination", "1", "amount", 0)))
                .isEqualTo(new Deposit("1", 0L));
    }

    @Test
    void acceptsIntegerValuedDouble() {
        Event event = EventParser.parse(map("type", "deposit", "destination", "1", "amount", 10.0));
        assertThat(event).isEqualTo(new Deposit("1", 10L));
    }

    @Test
    void rejectsNullBody() {
        assertThatThrownBy(() -> EventParser.parse(null))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining("JSON object");
    }

    @Test
    void rejectsMissingType() {
        assertThatThrownBy(() -> EventParser.parse(map("destination", "1", "amount", 10)))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining("type");
    }

    @Test
    void rejectsBlankType() {
        assertThatThrownBy(() -> EventParser.parse(map("type", "  ", "destination", "1", "amount", 10)))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    void rejectsNonStringType() {
        assertThatThrownBy(() -> EventParser.parse(map("type", 123, "amount", 10)))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    void rejectsUnknownType() {
        assertThatThrownBy(() -> EventParser.parse(map("type", "yeet", "amount", 10)))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining("yeet");
    }

    @Test
    void rejectsMissingAmount() {
        assertThatThrownBy(() -> EventParser.parse(map("type", "deposit", "destination", "1")))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void rejectsNonNumericAmount() {
        assertThatThrownBy(() -> EventParser.parse(map("type", "deposit", "destination", "1", "amount", "10")))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    void rejectsNonIntegerAmount() {
        assertThatThrownBy(() -> EventParser.parse(map("type", "deposit", "destination", "1", "amount", 10.5)))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining("whole number");
    }

    @Test
    void rejectsNaNAmount() {
        assertThatThrownBy(() -> EventParser.parse(map("type", "deposit", "destination", "1", "amount", Double.NaN)))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    void rejectsInfiniteAmount() {
        assertThatThrownBy(() -> EventParser.parse(
                map("type", "deposit", "destination", "1", "amount", Double.POSITIVE_INFINITY)))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    void rejectsMissingDestinationForDeposit() {
        assertThatThrownBy(() -> EventParser.parse(map("type", "deposit", "amount", 10)))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining("destination");
    }

    @Test
    void rejectsMissingOriginForWithdraw() {
        assertThatThrownBy(() -> EventParser.parse(map("type", "withdraw", "amount", 10)))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining("origin");
    }

    @Test
    void rejectsMissingDestinationForTransfer() {
        assertThatThrownBy(() -> EventParser.parse(map("type", "transfer", "origin", "1", "amount", 10)))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining("destination");
    }
}
