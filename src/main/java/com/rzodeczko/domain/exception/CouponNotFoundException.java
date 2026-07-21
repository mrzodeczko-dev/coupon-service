package com.rzodeczko.domain.exception;

public class CouponNotFoundException extends CouponDomainException {

    public CouponNotFoundException(String code) {
        super("Coupon with code '%s' not found".formatted(code));
    }
}
