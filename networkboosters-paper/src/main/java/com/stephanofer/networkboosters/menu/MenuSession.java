package com.stephanofer.networkboosters.menu;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MenuSession(
    UUID sessionToken,
    BoosterMenuFilter filter,
    BoosterMenuSort sort,
    int page,
    Optional<PendingActivation> activation,
    Optional<PendingTransfer> transfer
) {

    public MenuSession {
        Objects.requireNonNull(sessionToken, "sessionToken");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(sort, "sort");
        if (page < 1) {
            throw new IllegalArgumentException("page must be positive");
        }
        activation = Objects.requireNonNull(activation, "activation");
        transfer = Objects.requireNonNull(transfer, "transfer");
    }

    public static MenuSession initial() {
        return new MenuSession(UUID.randomUUID(), BoosterMenuFilter.ALL, BoosterMenuSort.RECOMMENDED, 1, Optional.empty(), Optional.empty());
    }

    public MenuSession withPage(int newPage) {
        return new MenuSession(sessionToken, filter, sort, Math.max(1, newPage), activation, transfer);
    }

    public MenuSession withFilter(BoosterMenuFilter newFilter) {
        return new MenuSession(sessionToken, newFilter, sort, 1, activation, transfer);
    }

    public MenuSession withSort(BoosterMenuSort newSort) {
        return new MenuSession(sessionToken, filter, newSort, 1, activation, transfer);
    }

    public MenuSession withActivation(PendingActivation pending) {
        return new MenuSession(sessionToken, filter, sort, page, Optional.of(pending), Optional.empty());
    }

    public MenuSession withTransfer(PendingTransfer pending) {
        return new MenuSession(sessionToken, filter, sort, page, Optional.empty(), Optional.of(pending));
    }

    public MenuSession clearPending() {
        return new MenuSession(sessionToken, filter, sort, page, Optional.empty(), Optional.empty());
    }
}
