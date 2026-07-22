package com.rzodeczko.domain.exception;

public class CouponAlreadyUsedByUserException extends CouponDomainException {

    public CouponAlreadyUsedByUserException(String code, String userId) {
        super("Coupon '%s' has already been used by user '%s'".formatted(code, userId));
    }
}
