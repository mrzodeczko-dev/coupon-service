package com.rzodeczko.application.service;

import com.rzodeczko.domain.exception.CouponAlreadyExistsException;
import com.rzodeczko.domain.exception.CouponAlreadyUsedByUserException;
import com.rzodeczko.domain.exception.CouponNotFoundException;
import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.domain.model.CouponCode;
import com.rzodeczko.domain.model.Country;
import com.rzodeczko.domain.repository.CouponRepository;
import com.rzodeczko.domain.repository.CouponUsageRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    @InjectMocks
    private CouponService couponService;

    private static final CouponCode CODE = new CouponCode("SUMMER");
    private static final Country PL = new Country("PL");

    @Nested
    class BuildCoupon {

        @Test
        void shouldBuildCouponWhenCodeDoesNotExist() {
            given(couponRepository.existsByCode(CODE)).willReturn(false);

            var coupon = couponService.buildCoupon(CODE, 100, PL);

            assertNotNull(coupon);
            assertEquals(CODE, coupon.getCode());
            assertEquals(100, coupon.getMaxUsages());
            assertEquals(PL, coupon.getCountry());
        }

        @Test
        void shouldThrowWhenCodeAlreadyExists() {
            given(couponRepository.existsByCode(CODE)).willReturn(true);

            assertThrows(CouponAlreadyExistsException.class,
                    () -> couponService.buildCoupon(CODE, 100, PL));
        }
    }

    @Nested
    class FindByCode {

        @Test
        void shouldReturnCouponWhenFound() {
            var coupon = Coupon.create(CODE, 10, PL);
            given(couponRepository.findByCode(CODE)).willReturn(Optional.of(coupon));

            var result = couponService.findByCode(CODE);

            assertEquals(coupon, result);
        }

        @Test
        void shouldThrowWhenCouponNotFound() {
            given(couponRepository.findByCode(CODE)).willReturn(Optional.empty());

            assertThrows(CouponNotFoundException.class,
                    () -> couponService.findByCode(CODE));
        }
    }

    @Nested
    class Save {

        @Test
        void shouldDelegateToRepository() {
            var coupon = Coupon.create(CODE, 10, PL);
            given(couponRepository.save(coupon)).willReturn(coupon);

            var result = couponService.save(coupon);

            assertEquals(coupon, result);
            verify(couponRepository).save(coupon);
        }
    }

    @Nested
    class ValidateUserNotUsed {

        @Test
        void shouldPassWhenUserHasNotUsedCoupon() {
            given(couponUsageRepository.existsByCodeAndUserId(CODE, "user-1")).willReturn(false);

            assertDoesNotThrow(() -> couponService.validateUserNotUsed(CODE, "user-1"));
        }

        @Test
        void shouldThrowWhenUserAlreadyUsedCoupon() {
            given(couponUsageRepository.existsByCodeAndUserId(CODE, "user-1")).willReturn(true);

            assertThrows(CouponAlreadyUsedByUserException.class,
                    () -> couponService.validateUserNotUsed(CODE, "user-1"));
        }
    }

    @Nested
    class RecordUsage {

        @Test
        void shouldDelegateToRepository() {
            couponService.recordUsage(CODE, "user-1");

            verify(couponUsageRepository).save(CODE, "user-1");
        }
    }

    @Nested
    class ExistsByCode {

        @Test
        void shouldReturnTrueWhenExists() {
            given(couponRepository.existsByCode(CODE)).willReturn(true);

            assertTrue(couponService.existsByCode(CODE));
        }

        @Test
        void shouldReturnFalseWhenNotExists() {
            given(couponRepository.existsByCode(CODE)).willReturn(false);

            assertFalse(couponService.existsByCode(CODE));
        }
    }
}
