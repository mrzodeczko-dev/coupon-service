package com.rzodeczko.domain.exception;

public class CouponAlreadyExistsException extends CouponDomainException {

    public CouponAlreadyExistsException(String code) {
        super("Coupon with code '%s' already exists".formatted(code));
    }
}
