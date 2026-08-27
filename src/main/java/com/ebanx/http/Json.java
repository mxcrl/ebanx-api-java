package com.ebanx.http;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, dependency-free JSON parser/serializer. Only supports what
 * this exercise needs - objects, strings, numbers, booleans, null,
 * and arrays for completeness - but is a real recursive-descent
 * parser, not a set of shortcuts, so it doesn't fall over on escaped
 * characters, nested structures, or malformed input.
 *
 * Kept dependency-free on purpose: no build tool is required, just
 * the JDK. If this project grows, swapping this class for Jackson
 * is a one-file change since nothing outside this package parses JSON.
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String input) {
        Parser parser = new Parser(input);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonParseException("Unexpected trailing content in JSON body");
        }
        return value;
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(Object value, StringBuilder sb) {
        switch (value) {
            case null -> sb.append("null");
            case String s -> writeString(s, sb);
            case Boolean b -> sb.append(b);
            case Map<?, ?> map -> writeObject(map, sb);
            case List<?> list -> writeArray(list, sb);
            case Double d -> writeNumber(d, sb);
            case Number n -> sb.append(n);
            default -> writeString(String.valueOf(value), sb);
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(String.valueOf(entry.getKey()), sb);
            sb.append(':');
            write(entry.getValue(), sb);
        }
        sb.append('}');
    }

    private static void writeArray(List<?> list, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            write(item, sb);
        }
        sb.append(']');
    }

    private static void writeNumber(double d, StringBuilder sb) {
        if (!Double.isInfinite(d) && !Double.isNaN(d) && d == Math.rint(d)) {
            sb.append((long) d);
        } else {
            sb.append(d);
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    public static final class JsonParseException extends RuntimeException {
        public JsonParseException(String message) {
            super(message);
        }
    }

    /**
     * Hand-rolled recursive-descent parser. Deliberately conservative:
     * anything it isn't sure how to read throws JsonParseException
     * rather than guessing, so malformed input always surfaces as a
     * clean 400 instead of a stack trace deep in business logic.
     */
    private static final class Parser {
        private final String input;
        private int pos;

        Parser(String input) {
            this.input = input == null ? "" : input;
        }

        boolean atEnd() {
            return pos >= input.length();
        }

        void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        char peek() {
            if (atEnd()) {
                throw new JsonParseException("Unexpected end of JSON input");
            }
            return input.charAt(pos);
        }

        char next() {
            char c = peek();
            pos++;
            return c;
        }

        void expect(char expected) {
            char c = next();
            if (c != expected) {
                throw new JsonParseException(
                        "Expected '" + expected + "' but found '" + c + "' at position " + (pos - 1));
            }
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (!atEnd() && peek() == '}') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                result.put(key, parseValue());
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    break;
                }
                if (c != ',') {
                    throw new JsonParseException("Expected ',' or '}' in object");
                }
            }
            return result;
        }

        List<Object> parseArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (!atEnd() && peek() == ']') {
                pos++;
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    break;
                }
                if (c != ',') {
                    throw new JsonParseException("Expected ',' or ']' in array");
                }
            }
            return result;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    appendEscaped(sb, next());
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        void appendEscaped(StringBuilder sb, char escaped) {
            switch (escaped) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (pos + 4 > input.length()) {
                        throw new JsonParseException("Truncated unicode escape");
                    }
                    String hex = input.substring(pos, pos + 4);
                    sb.append((char) Integer.parseInt(hex, 16));
                    pos += 4;
                }
                default -> throw new JsonParseException("Invalid escape sequence \\" + escaped);
            }
        }

        Boolean parseBoolean() {
            if (input.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (input.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new JsonParseException("Invalid literal at position " + pos);
        }

        Object parseNull() {
            if (input.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new JsonParseException("Invalid literal at position " + pos);
        }

        Number parseNumber() {
            int start = pos;
            if (!atEnd() && peek() == '-') {
                pos++;
            }
            while (!atEnd() && Character.isDigit(peek())) {
                pos++;
            }
            boolean isDouble = false;
            if (!atEnd() && peek() == '.') {
                isDouble = true;
                pos++;
                while (!atEnd() && Character.isDigit(peek())) {
                    pos++;
                }
            }
            if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
                isDouble = true;
                pos++;
                if (!atEnd() && (peek() == '+' || peek() == '-')) {
                    pos++;
                }
                while (!atEnd() && Character.isDigit(peek())) {
                    pos++;
                }
            }
            String numberText = input.substring(start, pos);
            if (numberText.isEmpty() || "-".equals(numberText)) {
                throw new JsonParseException("Invalid number at position " + start);
            }
            try {
                return isDouble ? Double.parseDouble(numberText) : Long.parseLong(numberText);
            } catch (NumberFormatException e) {
                throw new JsonParseException("Invalid number: " + numberText);
            }
        }
    }
}
