package com.rzodeczko.application.port.input;

public record CreateCouponCommand(
        String code,
        int maxUsages,
        String country
) {
    public CreateCouponCommand {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Coupon code must not be blank");
        }
        if (maxUsages <= 0) {
            throw new IllegalArgumentException("Max usages must be a positive number");
        }
        if (country == null || country.isBlank()) {
            throw new IllegalArgumentException("Country must not be blank");
        }
    }
}
