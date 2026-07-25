package com.rzodeczko.infrastructure.transaction;

import com.rzodeczko.application.service.CouponService;
import com.rzodeczko.domain.exception.CouponAlreadyExistsException;
import com.rzodeczko.domain.exception.CouponAlreadyUsedByUserException;
import com.rzodeczko.domain.exception.CouponConcurrentModificationException;
import com.rzodeczko.domain.model.Country;
import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.domain.model.CouponCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CouponTransactionBoundary {

    private final CouponService couponService;

    @Transactional(readOnly = true)
    public void validateUserNotUsed(CouponCode couponCode, String userId) {
        couponService.validateUserNotUsed(couponCode, userId);
    }

    @Transactional(readOnly = true)
    public Coupon findByCode(CouponCode code) {
        return couponService.findByCode(code);
    }

    @Transactional
    public Coupon executeUsage(CouponCode code, String userId, Country requestCountry) {

        Coupon coupon = couponService.findByCode(code);
        coupon.use(requestCountry);

        try {
            couponService.recordUsage(code, userId);
        } catch (DataIntegrityViolationException e) {
            throw new CouponAlreadyUsedByUserException(code.value(), userId);
        }

        try {
            return couponService.save(coupon);
        } catch (OptimisticLockingFailureException e) {
            throw new CouponConcurrentModificationException(code.value());
        }
    }


    @Transactional
    public Coupon save(CouponCode code, int maxUsages, Country country) {
        Coupon toSave = couponService.buildCoupon(code, maxUsages, country);
        try {
            return couponService.save(toSave);
        } catch (DataIntegrityViolationException e) {
            throw new CouponAlreadyExistsException(code.value());
        }
    }
}
