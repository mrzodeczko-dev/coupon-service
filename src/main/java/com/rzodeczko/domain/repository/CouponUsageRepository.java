package com.rzodeczko.domain.repository;

import com.rzodeczko.domain.model.CouponCode;

/**
 * Repository interface for tracking per-user coupon usage.
 */
public interface CouponUsageRepository {

    boolean existsByCodeAndUserId(CouponCode code, String userId);

    void save(CouponCode code, String userId);
}
