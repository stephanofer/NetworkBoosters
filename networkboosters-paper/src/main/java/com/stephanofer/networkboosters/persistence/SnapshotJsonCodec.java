package com.stephanofer.networkboosters.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class SnapshotJsonCodec {

    public String writeStringArray(Collection<String> values) {
        Objects.requireNonNull(values, "values");
        ArrayList<String> sorted = new ArrayList<>();
        for (String value : values) {
            sorted.add(Objects.requireNonNull(value, "value"));
        }
        sorted.sort(Comparator.naturalOrder());

        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < sorted.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            writeQuoted(builder, sorted.get(index));
        }
        return builder.append(']').toString();
    }

    public Set<String> readStringArray(String json, String label) {
        Parser parser = new Parser(Objects.requireNonNull(json, label), label);
        return parser.readStringArray();
    }

    private static void writeQuoted(StringBuilder builder, String value) {
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        builder.append('"');
    }

    private static final class Parser {

        private final String json;
        private final String label;
        private int index;

        private Parser(String json, String label) {
            this.json = json;
            this.label = label;
        }

        private Set<String> readStringArray() {
            skipWhitespace();
            expect('[');
            skipWhitespace();

            LinkedHashSet<String> values = new LinkedHashSet<>();
            if (peek(']')) {
                index++;
                skipWhitespace();
                expectEnd();
                return Set.copyOf(values);
            }

            while (true) {
                String value = readString();
                if (!values.add(value)) {
                    throw error("contains duplicate string: " + value);
                }
                skipWhitespace();
                if (peek(',')) {
                    index++;
                    skipWhitespace();
                    continue;
                }
                expect(']');
                skipWhitespace();
                expectEnd();
                return Set.copyOf(values);
            }
        }

        private String readString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < json.length()) {
                char character = json.charAt(index++);
                if (character == '"') {
                    return builder.toString();
                }
                if (character == '\\') {
                    builder.append(readEscape());
                } else {
                    if (character < 0x20) {
                        throw error("contains an unescaped control character");
                    }
                    builder.append(character);
                }
            }
            throw error("contains an unterminated string");
        }

        private char readEscape() {
            if (index >= json.length()) {
                throw error("contains an incomplete escape");
            }
            char escaped = json.charAt(index++);
            return switch (escaped) {
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> readUnicodeEscape();
                default -> throw error("contains an invalid escape: \\" + escaped);
            };
        }

        private char readUnicodeEscape() {
            if (index + 4 > json.length()) {
                throw error("contains an incomplete unicode escape");
            }
            int value = 0;
            for (int offset = 0; offset < 4; offset++) {
                char digit = json.charAt(index++);
                int hex = Character.digit(digit, 16);
                if (hex < 0) {
                    throw error("contains an invalid unicode escape");
                }
                value = (value << 4) + hex;
            }
            return (char) value;
        }

        private void skipWhitespace() {
            while (index < json.length()) {
                char character = json.charAt(index);
                if (character != ' ' && character != '\n' && character != '\r' && character != '\t') {
                    return;
                }
                index++;
            }
        }

        private boolean peek(char expected) {
            return index < json.length() && json.charAt(index) == expected;
        }

        private void expect(char expected) {
            if (index >= json.length() || json.charAt(index) != expected) {
                throw error("expected '" + expected + "'");
            }
            index++;
        }

        private void expectEnd() {
            if (index != json.length()) {
                throw error("contains trailing data");
            }
        }

        private PersistenceException error(String message) {
            return new PersistenceException(label + " " + message + " at offset " + index);
        }
    }
}
