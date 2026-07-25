package com.rzodeczko.infrastructure.transaction;

import com.rzodeczko.application.service.CouponService;
import com.rzodeczko.domain.exception.CouponAlreadyExistsException;
import com.rzodeczko.domain.exception.CouponAlreadyUsedByUserException;
import com.rzodeczko.domain.exception.CouponConcurrentModificationException;
import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.domain.model.CouponCode;
import com.rzodeczko.domain.model.Country;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CouponTransactionBoundaryTest {

    @Mock
    private CouponService couponService;

    @InjectMocks
    private CouponTransactionBoundary couponTransactionBoundary;

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC);
    private static final CouponCode CODE = new CouponCode("SUMMER26");
    private static final Country PL = new Country("PL");
    private static final String USER_ID = "user-1";

    @Nested
    class Save {

        @Test
        void shouldBuildAndSaveCoupon() {
            //given:
            var coupon = Coupon.create(CODE, 100, PL, FIXED_CLOCK);
            given(couponService.buildCoupon(CODE, 100, PL)).willReturn(coupon);
            given(couponService.save(coupon)).willReturn(coupon);

            //when:
            Coupon result = couponTransactionBoundary.save(CODE, 100, PL);

            //then:
            assertEquals(coupon, result);
            then(couponService).should().buildCoupon(CODE, 100, PL);
            then(couponService).should().save(coupon);
        }

        @Test
        void shouldThrowCouponAlreadyExistsWhenUniqueConstraintViolated() {
            //given:
            var coupon = Coupon.create(CODE, 100, PL, FIXED_CLOCK);
            given(couponService.buildCoupon(CODE, 100, PL)).willReturn(coupon);
            given(couponService.save(coupon)).willThrow(constraintViolation("uk_coupons_code"));

            //when + then:
            assertThrows(CouponAlreadyExistsException.class,
                    () -> couponTransactionBoundary.save(CODE, 100, PL));
        }

        @Test
        void shouldRethrowWhenUnknownConstraintViolatedOnSave() {
            //given:
            var coupon = Coupon.create(CODE, 100, PL, FIXED_CLOCK);
            given(couponService.buildCoupon(CODE, 100, PL)).willReturn(coupon);
            given(couponService.save(coupon)).willThrow(constraintViolation("some_other_constraint"));

            //when + then:
            assertThrows(DataIntegrityViolationException.class,
                    () -> couponTransactionBoundary.save(CODE, 100, PL));
        }
    }

    @Nested
    class ExecuteUsage {

        @Test
        void shouldFindCouponRecordUsageAndSave() {
            //given:
            var coupon = Coupon.create(CODE, 10, PL, FIXED_CLOCK);
            var savedCoupon = Coupon.create(CODE, 10, PL, FIXED_CLOCK);
            savedCoupon.use(PL);

            given(couponService.findByCode(CODE)).willReturn(coupon);
            given(couponService.save(coupon)).willReturn(savedCoupon);

            //when:
            Coupon result = couponTransactionBoundary.executeUsage(CODE, USER_ID, PL);

            //then:
            assertEquals(1, result.getCurrentUsages());
            var inOrder = inOrder(couponService);
            inOrder.verify(couponService).findByCode(CODE);
            inOrder.verify(couponService).recordUsage(CODE, USER_ID);
            inOrder.verify(couponService).save(coupon);
        }

        @Test
        void shouldThrowAlreadyUsedWhenRecordUsageViolatesUniqueConstraint() {
            //given:
            var coupon = Coupon.create(CODE, 10, PL, FIXED_CLOCK);
            given(couponService.findByCode(CODE)).willReturn(coupon);
            willThrow(constraintViolation("uq_coupon_usages_code_user_id"))
                    .given(couponService).recordUsage(CODE, USER_ID);

            //when + then:
            assertThrows(CouponAlreadyUsedByUserException.class,
                    () -> couponTransactionBoundary.executeUsage(CODE, USER_ID, PL));

            then(couponService).should(never()).save(any());
        }

        @Test
        void shouldRethrowWhenUnknownConstraintViolatedOnRecordUsage() {
            //given:
            var coupon = Coupon.create(CODE, 10, PL, FIXED_CLOCK);
            given(couponService.findByCode(CODE)).willReturn(coupon);
            willThrow(constraintViolation("fk_coupon_usages_code"))
                    .given(couponService).recordUsage(CODE, USER_ID);

            //when + then:
            assertThrows(DataIntegrityViolationException.class,
                    () -> couponTransactionBoundary.executeUsage(CODE, USER_ID, PL));

            then(couponService).should(never()).save(any());
        }

        @Test
        void shouldThrowConcurrentModificationWhenOptimisticLockFails() {
            //given:
            var coupon = Coupon.create(CODE, 10, PL, FIXED_CLOCK);
            given(couponService.findByCode(CODE)).willReturn(coupon);
            given(couponService.save(coupon)).willThrow(new OptimisticLockingFailureException("version mismatch"));

            //when + then:
            assertThrows(CouponConcurrentModificationException.class,
                    () -> couponTransactionBoundary.executeUsage(CODE, USER_ID, PL));
        }
    }

    @Nested
    class ValidateUserNotUsed {

        @Test
        void shouldDelegateToService() {
            //when:
            couponTransactionBoundary.validateUserNotUsed(CODE, USER_ID);

            //then:
            then(couponService).should().validateUserNotUsed(CODE, USER_ID);
        }
    }

    @Nested
    class FindByCode {

        @Test
        void shouldDelegateToService() {
            //given:
            var coupon = Coupon.create(CODE, 10, PL, FIXED_CLOCK);
            given(couponService.findByCode(CODE)).willReturn(coupon);

            //when:
            Coupon result = couponTransactionBoundary.findByCode(CODE);

            //then:
            assertEquals(coupon, result);
            then(couponService).should().findByCode(CODE);
        }
    }

    private static DataIntegrityViolationException constraintViolation(String constraintName) {
        var cause = new ConstraintViolationException(
                "constraint violation", new SQLException(), constraintName);
        return new DataIntegrityViolationException("integrity violation", cause);
    }
}
