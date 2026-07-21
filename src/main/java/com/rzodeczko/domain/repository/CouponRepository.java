package com.rzodeczko.domain.repository;

import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.domain.model.CouponCode;

import java.util.Optional;

/**
 * Repository interface for managing Coupon aggregates in the persistence layer
 */
public interface CouponRepository {

    Coupon save(Coupon coupon);

    Optional<Coupon> findByCode(CouponCode code);

    boolean existsByCode(CouponCode code);
}
