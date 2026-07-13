package com.stephanofer.networkboosters.menu;

public enum BoosterMenuSort {
    RECOMMENDED,
    QUANTITY;

    public BoosterMenuSort next() {
        BoosterMenuSort[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
