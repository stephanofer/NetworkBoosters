package com.stephanofer.networkboosters.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DurationFormatterTest {

    @Test
    void usesAtMostTwoLocalizedUnitsAndHandlesExpiredDurations() {
        LocalizationSnapshot localization = localization();
        DurationFormatter formatter = new DurationFormatter();

        assertEquals("1d 2h", formatter.format(Duration.ofHours(26).plusMinutes(10), localization, "en"));
        assertEquals("now", formatter.format(Duration.ZERO, localization, "en"));
        assertEquals("now", formatter.format(Duration.ofSeconds(-1), localization, "en"));
    }

    private static LocalizationSnapshot localization() {
        Map<String, List<String>> messages = Map.of(
            MessageKey.TIME_NOW.path(), List.of("now"),
            MessageKey.TIME_DAY.path(), List.of("d"),
            MessageKey.TIME_DAYS.path(), List.of("d"),
            MessageKey.TIME_HOUR.path(), List.of("h"),
            MessageKey.TIME_HOURS.path(), List.of("h"),
            MessageKey.TIME_MINUTE.path(), List.of("m"),
            MessageKey.TIME_MINUTES.path(), List.of("m"),
            MessageKey.TIME_SECOND.path(), List.of("s"),
            MessageKey.TIME_SECONDS.path(), List.of("s")
        );
        MessageCatalog catalog = new MessageCatalog("en", messages, Map.of());
        return new LocalizationSnapshot("en", "en", Map.of("en", catalog));
    }
}
