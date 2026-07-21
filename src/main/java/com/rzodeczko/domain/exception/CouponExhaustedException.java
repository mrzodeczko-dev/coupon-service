package com.rzodeczko.domain.exception;

public class CouponExhaustedException extends CouponDomainException {

    public CouponExhaustedException(String code) {
        super("Coupon '%s' has reached its maximum number of usages".formatted(code));
    }
}
