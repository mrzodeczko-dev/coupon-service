package com.rzodeczko.infrastructure.persistence.repository;

import com.rzodeczko.infrastructure.persistence.entity.CouponUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCouponUsageRepository extends JpaRepository<CouponUsageEntity, Long> {

    boolean existsByCodeAndUserId(String code, String userId);
}
