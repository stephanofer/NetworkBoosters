package com.stephanofer.networkboosters.localization;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record LocalizationSnapshot(
    String fallbackLanguage,
    String consoleLanguage,
    Map<String, MessageCatalog> catalogs
) {

    public LocalizationSnapshot {
        fallbackLanguage = normalizeLanguage(fallbackLanguage, "fallbackLanguage");
        consoleLanguage = normalizeLanguage(consoleLanguage, "consoleLanguage");
        catalogs = Map.copyOf(Objects.requireNonNull(catalogs, "catalogs"));
        if (!catalogs.containsKey(fallbackLanguage)) {
            throw new IllegalArgumentException("fallback language catalog is missing: " + fallbackLanguage);
        }
        if (!catalogs.containsKey(consoleLanguage)) {
            throw new IllegalArgumentException("console language catalog is missing: " + consoleLanguage);
        }
    }

    public MessageCatalog catalog(String language) {
        return this.catalogs.getOrDefault(normalizeLanguage(language, "language"), this.catalogs.get(this.fallbackLanguage));
    }

    public Optional<String> template(String language, MessageKey key) {
        MessageCatalog catalog = this.catalog(language);
        Optional<String> value = catalog.template(key);
        return value.isPresent() ? value : this.catalogs.get(this.fallbackLanguage).template(key);
    }

    public List<String> lines(String language, MessageKey key) {
        MessageCatalog catalog = this.catalog(language);
        List<String> value = catalog.lines(key);
        return value.isEmpty() ? this.catalogs.get(this.fallbackLanguage).lines(key) : value;
    }

    static String normalizeLanguage(String raw, String label) {
        String value = Objects.requireNonNull(raw, label).trim().toLowerCase(java.util.Locale.ROOT);
        if (!value.equals("es") && !value.equals("en")) {
            throw new IllegalArgumentException(label + " must be es or en");
        }
        return value;
    }
}
