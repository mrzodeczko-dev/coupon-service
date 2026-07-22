package com.rzodeczko.infrastructure.persistence.repository.adapter;

import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.domain.model.CouponCode;
import com.rzodeczko.domain.repository.CouponRepository;
import com.rzodeczko.infrastructure.persistence.entity.CouponEntity;
import com.rzodeczko.infrastructure.persistence.mapper.CouponMapper;
import com.rzodeczko.infrastructure.persistence.entity.CouponUsageEntity;
import com.rzodeczko.infrastructure.persistence.repository.JpaCouponRepository;
import com.rzodeczko.infrastructure.persistence.repository.JpaCouponUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaCouponRepositoryAdapter implements CouponRepository {

    private final JpaCouponRepository jpaCouponRepository;
    private final JpaCouponUsageRepository jpaCouponUsageRepository;
    private final CouponMapper couponMapper;

    @Override
    @Transactional
    public Coupon save(Coupon coupon) {
        CouponEntity entity = jpaCouponRepository
                .findById(coupon.getId())
                .map(existing -> {
                    existing.setCurrentUsages(coupon.getCurrentUsages());
                    return existing;
                })
                .orElseGet(() -> couponMapper.toEntity(coupon));

        return couponMapper.toDomain(jpaCouponRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Coupon> findByCode(CouponCode code) {
        return jpaCouponRepository
                .findByCode(code.value())
                .map(couponMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCode(CouponCode code) {
        return jpaCouponRepository.existsByCode(code.value());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsUsageByCodeAndUserId(CouponCode code, String userId) {
        return jpaCouponUsageRepository.existsByCodeAndUserId(code.value(), userId);
    }

    @Override
    @Transactional
    public void saveUsage(CouponCode code, String userId) {
        var usage = CouponUsageEntity.builder()
                .code(code.value())
                .userId(userId)
                .build();
        jpaCouponUsageRepository.saveAndFlush(usage);
    }
}
