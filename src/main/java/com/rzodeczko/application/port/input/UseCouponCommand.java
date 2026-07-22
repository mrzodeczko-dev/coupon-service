package com.rzodeczko.application.port.input;

public record UseCouponCommand(
        String code,
        String userId,
        String ipAddress
) {
    public UseCouponCommand {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Coupon code must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be blank");
        }
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("IP address must not be blank");
        }
    }
}
