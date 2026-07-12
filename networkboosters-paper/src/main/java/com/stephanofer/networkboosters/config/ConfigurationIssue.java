package com.stephanofer.networkboosters.config;

import java.util.Objects;

public record ConfigurationIssue(
    Severity severity,
    String file,
    String path,
    String message
) {

    public ConfigurationIssue {
        Objects.requireNonNull(severity, "severity");
        file = normalize(file, "file");
        path = normalize(path, "path");
        message = normalize(message, "message");
    }

    public static ConfigurationIssue error(String file, String path, String message) {
        return new ConfigurationIssue(Severity.ERROR, file, path, message);
    }

    public static ConfigurationIssue warning(String file, String path, String message) {
        return new ConfigurationIssue(Severity.WARNING, file, path, message);
    }

    private static String normalize(String raw, String label) {
        String value = Objects.requireNonNull(raw, label).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        return value;
    }

    public enum Severity {
        ERROR,
        WARNING
    }
}
