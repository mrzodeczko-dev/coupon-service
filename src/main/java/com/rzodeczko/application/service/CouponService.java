package com.rzodeczko.application.service;

import com.rzodeczko.domain.exception.CouponAlreadyExistsException;
import com.rzodeczko.domain.exception.CouponAlreadyUsedByUserException;
import com.rzodeczko.domain.exception.CouponNotFoundException;
import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.domain.model.CouponCode;
import com.rzodeczko.domain.model.Country;
import com.rzodeczko.domain.repository.CouponRepository;

/**
 * Application service providing domain-level coupon operations.
 * Pure Java - no framework dependencies.
 */
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    public boolean existsByCode(CouponCode code) {
        return couponRepository.existsByCode(code);
    }

    public Coupon buildCoupon(CouponCode code, int maxUsages, Country country) {
        if (couponRepository.existsByCode(code)) {
            throw new CouponAlreadyExistsException(code.value());
        }
        return Coupon.create(code, maxUsages, country);
    }

    public Coupon save(Coupon coupon) {
        return couponRepository.save(coupon);
    }

    public Coupon findByCode(CouponCode code) {
        return couponRepository.findByCode(code)
                .orElseThrow(() -> new CouponNotFoundException(code.value()));
    }

    public void validateUserNotUsed(CouponCode code, String userId) {
        if (couponRepository.existsUsageByCodeAndUserId(code, userId)) {
            throw new CouponAlreadyUsedByUserException(code.value(), userId);
        }
    }

    public void recordUsage(CouponCode code, String userId) {
        couponRepository.saveUsage(code, userId);
    }
}
