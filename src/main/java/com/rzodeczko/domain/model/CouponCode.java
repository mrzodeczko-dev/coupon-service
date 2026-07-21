package com.rzodeczko.domain.model;

import java.util.Locale;

public record CouponCode(String value) {

    public CouponCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Coupon code must not be blank");
        }
        value = value.toUpperCase(Locale.ROOT);
    }
}
