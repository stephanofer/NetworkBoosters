package com.stephanofer.networkboosters.localization;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record MessageCatalog(
    String language,
    Map<String, List<String>> messages,
    Map<String, BoosterTranslation> boosters
) {

    public MessageCatalog {
        language = LocalizationSnapshot.normalizeLanguage(language, "language");
        messages = Map.copyOf(Objects.requireNonNull(messages, "messages"));
        boosters = Map.copyOf(Objects.requireNonNull(boosters, "boosters"));
    }

    public Optional<String> template(MessageKey key) {
        List<String> values = this.messages.get(key.path());
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(values.getFirst());
    }

    public List<String> lines(MessageKey key) {
        return this.messages.getOrDefault(key.path(), List.of());
    }

    public Optional<BoosterTranslation> booster(String boosterId) {
        return Optional.ofNullable(this.boosters.get(Objects.requireNonNull(boosterId, "boosterId")));
    }

    public record BoosterTranslation(String name, List<String> description) {
        public BoosterTranslation {
            if (Objects.requireNonNull(name, "name").isBlank()) {
                throw new IllegalArgumentException("name cannot be blank");
            }
            description = List.copyOf(Objects.requireNonNull(description, "description"));
        }
    }
}
