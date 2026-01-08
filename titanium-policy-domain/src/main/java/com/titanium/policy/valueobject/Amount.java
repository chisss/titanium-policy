package com.titanium.policy.valueobject;

import java.math.BigDecimal;

public record Amount(BigDecimal value, String currency) {
    public static Amount of(BigDecimal value, String currency) {
        return new Amount(value, currency);
    }

    public static Amount of(double value, String currency) {
        return new Amount(BigDecimal.valueOf(value), currency);
    }

    public static Amount add(Amount a, Amount b) {
        if (!a.currency().equals(b.currency())) {
            throw new IllegalArgumentException("Currencies must be the same");
        }
        return new Amount(a.value().add(b.value()), a.currency());
    }
}
