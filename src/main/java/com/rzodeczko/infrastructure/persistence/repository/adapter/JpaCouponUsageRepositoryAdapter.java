package com.rzodeczko.infrastructure.persistence.repository.adapter;

import com.rzodeczko.domain.model.CouponCode;
import com.rzodeczko.domain.repository.CouponUsageRepository;
import com.rzodeczko.infrastructure.persistence.entity.CouponUsageEntity;
import com.rzodeczko.infrastructure.persistence.repository.JpaCouponUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaCouponUsageRepositoryAdapter implements CouponUsageRepository {

    private final JpaCouponUsageRepository jpaCouponUsageRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCodeAndUserId(CouponCode code, String userId) {
        return jpaCouponUsageRepository.existsByCodeAndUserId(code.value(), userId);
    }

    @Override
    @Transactional
    public void save(CouponCode code, String userId) {
        var usage = CouponUsageEntity.builder()
                .code(code.value())
                .userId(userId)
                .build();
        jpaCouponUsageRepository.saveAndFlush(usage);
    }
}
