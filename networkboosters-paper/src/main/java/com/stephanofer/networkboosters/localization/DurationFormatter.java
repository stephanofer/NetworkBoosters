package com.stephanofer.networkboosters.localization;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class DurationFormatter {

    public String format(Duration duration, LocalizationSnapshot localization, String language) {
        if (duration.isZero() || duration.isNegative()) {
            return localization.template(language, MessageKey.TIME_NOW).orElse("now");
        }
        long seconds = duration.getSeconds();
        long days = seconds / 86_400;
        seconds %= 86_400;
        long hours = seconds / 3_600;
        seconds %= 3_600;
        long minutes = seconds / 60;
        seconds %= 60;

        List<String> parts = new ArrayList<>(2);
        add(parts, days, MessageKey.TIME_DAY, MessageKey.TIME_DAYS, localization, language);
        add(parts, hours, MessageKey.TIME_HOUR, MessageKey.TIME_HOURS, localization, language);
        add(parts, minutes, MessageKey.TIME_MINUTE, MessageKey.TIME_MINUTES, localization, language);
        add(parts, seconds, MessageKey.TIME_SECOND, MessageKey.TIME_SECONDS, localization, language);
        if (parts.isEmpty()) {
            return localization.template(language, MessageKey.TIME_NOW).orElse("now");
        }
        return String.join(" ", parts.subList(0, Math.min(2, parts.size())));
    }

    private static void add(
        List<String> parts,
        long amount,
        MessageKey singular,
        MessageKey plural,
        LocalizationSnapshot localization,
        String language
    ) {
        if (amount <= 0) {
            return;
        }
        MessageKey key = amount == 1 ? singular : plural;
        String unit = localization.template(language, key).orElse(key.path());
        parts.add(amount + unit);
    }
}
