package com.rzodeczko.infrastructure.persistence.repository.adapter;

import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.domain.model.CouponCode;
import com.rzodeczko.domain.repository.CouponRepository;
import com.rzodeczko.infrastructure.persistence.mapper.CouponMapper;
import com.rzodeczko.infrastructure.persistence.repository.JpaCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaCouponRepositoryAdapter implements CouponRepository {

    private final JpaCouponRepository jpaCouponRepository;
    private final CouponMapper couponMapper;

    @Override
    @Transactional
    public Coupon save(Coupon coupon) {
        var entity = couponMapper.toEntity(coupon);
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
}
