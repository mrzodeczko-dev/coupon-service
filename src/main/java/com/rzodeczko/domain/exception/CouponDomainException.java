package com.rzodeczko.domain.exception;

/**
 * Base class for all coupon domain exceptions.
 * Allows infrastructure layers to catch domain errors uniformly.
 */
public abstract class CouponDomainException extends RuntimeException {

    protected CouponDomainException(String message) {
        super(message);
    }
}
