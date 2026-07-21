package com.rzodeczko.domain.exception;

public class CouponCountryMismatchException extends CouponDomainException {

    public CouponCountryMismatchException(String couponCountry, String requestCountry) {
        super("Coupon is restricted to country '%s', but request originated from '%s'"
                .formatted(couponCountry, requestCountry));
    }
}
