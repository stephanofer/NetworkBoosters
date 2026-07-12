package com.stephanofer.networkboosters.config;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

public final class DurationParser {

    private DurationParser() {
    }

    public static Duration positive(String raw, String path) {
        Duration duration = parse(raw, path, false);
        if (duration.isZero()) {
            throw new IllegalArgumentException(path + " must be positive");
        }
        return duration;
    }

    public static Duration nonNegative(String raw, String path) {
        return parse(raw, path, true);
    }

    private static Duration parse(String raw, String path, boolean allowZero) {
        String value = Objects.requireNonNull(raw, path).trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path + " cannot be empty");
        }
        if (value.indexOf(' ') >= 0) {
            throw new IllegalArgumentException(path + " cannot contain spaces");
        }

        Unit unit = Unit.resolve(value, path);
        String number = value.substring(0, value.length() - unit.suffix().length());
        if (number.isEmpty()) {
            throw new IllegalArgumentException(path + " must contain a number");
        }

        try {
            long amount = Long.parseLong(number);
            if (amount < 0 || (!allowZero && amount == 0)) {
                throw new IllegalArgumentException(path + (allowZero ? " cannot be negative" : " must be positive"));
            }
            return Duration.ofMillis(Math.multiplyExact(amount, unit.millis()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(path + " has invalid duration: " + raw, exception);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(path + " duration is too large: " + raw, exception);
        }
    }

    private record Unit(String suffix, long millis) {

        private static final Unit[] VALUES = {
            new Unit("ms", 1L),
            new Unit("s", 1_000L),
            new Unit("m", 60_000L),
            new Unit("h", 3_600_000L),
            new Unit("d", 86_400_000L)
        };

        private static Unit resolve(String value, String path) {
            for (Unit unit : VALUES) {
                if (value.endsWith(unit.suffix())) {
                    return unit;
                }
            }
            throw new IllegalArgumentException(path + " must use ms, s, m, h, or d suffix");
        }
    }
}
