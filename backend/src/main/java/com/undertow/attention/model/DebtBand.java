package com.undertow.attention.model;

public enum DebtBand {
    LOW,
    MODERATE,
    HIGH,
    OVERLOADED;

    public static DebtBand fromScore(double normalizedDebt) {
        if (normalizedDebt <= 30) return LOW;
        if (normalizedDebt <= 60) return MODERATE;
        if (normalizedDebt <= 80) return HIGH;
        return OVERLOADED;
    }
}
