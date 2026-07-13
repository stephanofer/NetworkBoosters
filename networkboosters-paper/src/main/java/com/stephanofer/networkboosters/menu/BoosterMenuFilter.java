package com.stephanofer.networkboosters.menu;

public enum BoosterMenuFilter {
    ALL,
    ACTIVE,
    CURRENT_CONTEXT,
    POINTS,
    LOCKED,
    TRANSFERABLE;

    public BoosterMenuFilter next() {
        BoosterMenuFilter[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
