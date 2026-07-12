package com.stephanofer.networkboosters.config;

import java.util.List;
import java.util.Objects;

public final class ConfigurationException extends RuntimeException {

    private final List<ConfigurationIssue> issues;

    public ConfigurationException(List<ConfigurationIssue> issues) {
        super(formatMessage(issues));
        if (issues == null || issues.isEmpty()) {
            throw new IllegalArgumentException("issues cannot be empty");
        }
        this.issues = List.copyOf(issues);
    }

    public List<ConfigurationIssue> issues() {
        return this.issues;
    }

    private static String formatMessage(List<ConfigurationIssue> issues) {
        Objects.requireNonNull(issues, "issues");
        long errors = issues.stream().filter(issue -> issue.severity() == ConfigurationIssue.Severity.ERROR).count();
        StringBuilder builder = new StringBuilder("NetworkBoosters configuration contains ")
            .append(errors)
            .append(errors == 1 ? " error" : " errors");

        for (ConfigurationIssue issue : issues) {
            builder.append(System.lineSeparator())
                .append("- ")
                .append(issue.file())
                .append(':')
                .append(issue.path())
                .append(System.lineSeparator())
                .append("  ")
                .append(issue.message());
        }
        return builder.toString();
    }
}
