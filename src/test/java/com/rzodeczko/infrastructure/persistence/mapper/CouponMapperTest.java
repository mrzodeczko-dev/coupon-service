package com.rzodeczko.infrastructure.persistence.mapper;

import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.domain.model.CouponCode;
import com.rzodeczko.domain.model.Country;
import com.rzodeczko.infrastructure.persistence.entity.CouponEntity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CouponMapperTest {

    private final CouponMapper mapper = new CouponMapper();

    private static final UUID ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    @Nested
    class ToEntity {

        @Test
        void shouldMapAllDomainFieldsToEntity() {
            var coupon = Coupon.reconstitute(ID, new CouponCode("SUMMER"), NOW, 100, 5, new Country("PL"));

            var entity = mapper.toEntity(coupon);

            assertEquals(ID, entity.getId());
            assertEquals("SUMMER", entity.getCode());
            assertEquals(NOW, entity.getCreatedAt());
            assertEquals(100, entity.getMaxUsages());
            assertEquals(5, entity.getCurrentUsages());
            assertEquals("PL", entity.getCountry());
        }
    }

    @Nested
    class ToDomain {

        @Test
        void shouldMapAllEntityFieldsToDomain() {
            var entity = CouponEntity.builder()
                    .id(ID)
                    .code("WINTER")
                    .createdAt(NOW)
                    .maxUsages(50)
                    .currentUsages(10)
                    .country("DE")
                    .build();

            var coupon = mapper.toDomain(entity);

            assertEquals(ID, coupon.getId());
            assertEquals(new CouponCode("WINTER"), coupon.getCode());
            assertEquals(NOW, coupon.getCreatedAt());
            assertEquals(50, coupon.getMaxUsages());
            assertEquals(10, coupon.getCurrentUsages());
            assertEquals(new Country("DE"), coupon.getCountry());
        }
    }

    @Nested
    class RoundTrip {

        @Test
        void shouldPreserveAllDataOnRoundTrip() {
            var original = Coupon.reconstitute(ID, new CouponCode("PROMO"), NOW, 200, 42, new Country("US"));

            var entity = mapper.toEntity(original);
            var restored = mapper.toDomain(entity);

            assertEquals(original.getId(), restored.getId());
            assertEquals(original.getCode(), restored.getCode());
            assertEquals(original.getCreatedAt(), restored.getCreatedAt());
            assertEquals(original.getMaxUsages(), restored.getMaxUsages());
            assertEquals(original.getCurrentUsages(), restored.getCurrentUsages());
            assertEquals(original.getCountry(), restored.getCountry());
        }
    }
}
