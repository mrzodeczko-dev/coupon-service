package com.rzodeczko.infrastructure.persistence.mapper;

import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.domain.model.CouponCode;
import com.rzodeczko.domain.model.Country;
import com.rzodeczko.infrastructure.persistence.entity.CouponEntity;
import org.springframework.stereotype.Component;

@Component
public class CouponMapper {

    public CouponEntity toEntity(Coupon domain) {
        return CouponEntity.builder()
                .id(domain.getId())
                .code(domain.getCode().value())
                .createdAt(domain.getCreatedAt())
                .maxUsages(domain.getMaxUsages())
                .currentUsages(domain.getCurrentUsages())
                .country(domain.getCountry().code())
                .version(domain.getVersion())
                .build();
    }

    public Coupon toDomain(CouponEntity entity) {
        return Coupon.reconstitute(
                entity.getId(),
                new CouponCode(entity.getCode()),
                entity.getCreatedAt(),
                entity.getMaxUsages(),
                entity.getCurrentUsages(),
                new Country(entity.getCountry()),
                entity.getVersion()
        );
    }
}
