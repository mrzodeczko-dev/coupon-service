package com.rzodeczko.domain.exception;

public class CouponConcurrentModificationException extends CouponDomainException {

    public CouponConcurrentModificationException(String code) {
        super("Coupon '%s' was modified concurrently, please retry".formatted(code));
    }
}
