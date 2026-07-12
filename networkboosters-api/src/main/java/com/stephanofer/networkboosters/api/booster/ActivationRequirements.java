package com.stephanofer.networkboosters.api.booster;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public record ActivationRequirements(Set<String> permissions, PermissionMode mode) {

    public static final ActivationRequirements NONE = new ActivationRequirements(Set.of(), PermissionMode.ALL);

    public ActivationRequirements {
        mode = Objects.requireNonNull(mode, "mode");
        permissions = normalizePermissions(permissions);
    }

    public boolean satisfiedBy(Predicate<String> permissionChecker) {
        Objects.requireNonNull(permissionChecker, "permissionChecker");
        if (permissions.isEmpty()) {
            return true;
        }
        return switch (mode) {
            case ALL -> permissions.stream().allMatch(permissionChecker);
            case ANY -> permissions.stream().anyMatch(permissionChecker);
        };
    }

    private static Set<String> normalizePermissions(Set<String> rawPermissions) {
        Objects.requireNonNull(rawPermissions, "permissions");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String permission : rawPermissions) {
            String value = Objects.requireNonNull(permission, "permission").trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Permission cannot be empty");
            }
            if (!normalized.add(value)) {
                throw new IllegalArgumentException("Duplicate permission: " + value);
            }
        }
        return Set.copyOf(normalized);
    }
}
