package com.rzodeczko.infrastructure.usecase;

import com.rzodeczko.application.port.input.CreateCouponCommand;
import com.rzodeczko.application.port.input.UseCouponCommand;
import com.rzodeczko.application.port.output.GeoLocationProvider;
import com.rzodeczko.application.service.CouponService;
import com.rzodeczko.domain.exception.CouponAlreadyExistsException;
import com.rzodeczko.domain.exception.CouponAlreadyUsedByUserException;
import com.rzodeczko.domain.exception.CouponConcurrentModificationException;
import com.rzodeczko.domain.exception.CouponCountryMismatchException;
import com.rzodeczko.domain.exception.CouponExhaustedException;
import com.rzodeczko.domain.exception.CouponNotFoundException;
import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.domain.model.CouponCode;
import com.rzodeczko.domain.model.Country;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CouponUseCaseImplTest {

    @Mock
    private CouponService couponService;

    @Mock
    private GeoLocationProvider geoLocationProvider;

    @InjectMocks
    private CouponUseCaseImpl couponUseCase;

    @Nested
    class Create {

        @Test
        void shouldCreateAndSaveCoupon() {
            //given:
            var command = new CreateCouponCommand("SUMMER25", 100, "PL");
            var code = new CouponCode("SUMMER25");
            var country = new Country("PL");
            var builtCoupon = Coupon.create(code, 100, country);

            given(couponService.buildCoupon(code, 100, country)).willReturn(builtCoupon);
            given(couponService.save(builtCoupon)).willReturn(builtCoupon);

            //when:
            Coupon result = couponUseCase.create(command);

            //then:
            assertNotNull(result);
            assertEquals("SUMMER25", result.getCode().value());
            assertEquals("PL", result.getCountry().code());
            then(couponService).should().buildCoupon(code, 100, country);
            then(couponService).should().save(builtCoupon);
        }

        @Test
        void shouldPropagateExceptionWhenCodeAlreadyExists() {
            //given:
            var command = new CreateCouponCommand("SUMMER25", 100, "PL");

            given(couponService.buildCoupon(any(), anyInt(), any()))
                    .willThrow(new CouponAlreadyExistsException("SUMMER25"));

            //when+then:
            assertThrows(CouponAlreadyExistsException.class,
                    () -> couponUseCase.create(command));
        }
    }

    @Nested
    class Use {

        @Test
        void shouldResolveCountryAndUseCoupon() {
            //given:
            var command = new UseCouponCommand("SUMMER25", "user-1", "89.64.55.1");
            var code = new CouponCode("SUMMER25");
            var country = new Country("PL");
            var coupon = Coupon.create(code, 10, country);

            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(country);
            given(couponService.findByCode(code)).willReturn(coupon);
            given(couponService.save(coupon)).willReturn(coupon);

            //when:
            Coupon result = couponUseCase.use(command);

            //then:
            assertEquals(1, result.getCurrentUsages());
            assertEquals(9, result.getRemainingUsages());
            then(couponService).should().validateUserNotUsed(code, "user-1");
            then(geoLocationProvider).should().resolveCountry("89.64.55.1");
            then(couponService).should().findByCode(code);
            then(couponService).should().save(coupon);
            then(couponService).should().recordUsage(code, "user-1");
        }

        @Test
        void shouldThrowWhenUserAlreadyUsedCoupon() {
            //given:
            var command = new UseCouponCommand("SUMMER25", "user-1", "89.64.55.1");
            var code = new CouponCode("SUMMER25");

            willThrow(new CouponAlreadyUsedByUserException("SUMMER25", "user-1"))
                    .given(couponService).validateUserNotUsed(code, "user-1");

            //when + then
            assertThrows(CouponAlreadyUsedByUserException.class,
                    () -> couponUseCase.use(command));
        }

        @Test
        void shouldThrowWhenCouponNotFound() {
            //given:
            var command = new UseCouponCommand("UNKNOWN", "user-1", "89.64.55.1");

            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(new Country("PL"));
            given(couponService.findByCode(new CouponCode("UNKNOWN")))
                    .willThrow(new CouponNotFoundException("UNKNOWN"));

            //when + then
            assertThrows(CouponNotFoundException.class,
                    () -> couponUseCase.use(command));
        }

        @Test
        void shouldThrowWhenCountryMismatch() {
            var command = new UseCouponCommand("SUMMER25", "user-1", "8.8.8.8");
            var code = new CouponCode("SUMMER25");
            var coupon = Coupon.create(code, 10, new Country("PL"));

            given(geoLocationProvider.resolveCountry("8.8.8.8")).willReturn(new Country("US"));
            given(couponService.findByCode(code)).willReturn(coupon);

            assertThrows(CouponCountryMismatchException.class,
                    () -> couponUseCase.use(command));
        }

        @Test
        void shouldThrowConcurrentModificationWhenOptimisticLockFails() {
            //given
            var command = new UseCouponCommand("SUMMER25", "user-1", "89.64.55.1");
            var code = new CouponCode("SUMMER25");
            var country = new Country("PL");
            var coupon = Coupon.create(code, 10, country);

            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(country);
            given(couponService.findByCode(code)).willReturn(coupon);
            given(couponService.save(coupon)).willThrow(new OptimisticLockingFailureException("version conflict"));

            //when + then
            assertThrows(CouponConcurrentModificationException.class,
                    () -> couponUseCase.use(command));
        }

        @Test
        void shouldThrowAlreadyUsedWhenDuplicateUsageRaceCondition() {
            //given
            var command = new UseCouponCommand("SUMMER25", "user-1", "89.64.55.1");
            var code = new CouponCode("SUMMER25");
            var country = new Country("PL");
            var coupon = Coupon.create(code, 10, country);

            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(country);
            given(couponService.findByCode(code)).willReturn(coupon);
            given(couponService.save(coupon)).willReturn(coupon);
            willThrow(new DataIntegrityViolationException("unique constraint"))
                    .given(couponService).recordUsage(code, "user-1");

            //when + then
            assertThrows(CouponAlreadyUsedByUserException.class,
                    () -> couponUseCase.use(command));
        }

        @Test
        void shouldThrowWhenCouponExhausted() {
            //given
            var command = new UseCouponCommand("SUMMER25", "user-1", "89.64.55.1");
            var code = new CouponCode("SUMMER25");
            var country = new Country("PL");
            var coupon = Coupon.reconstitute(
                    java.util.UUID.randomUUID(), code, java.time.Instant.now(), 1, 1, country, 0L
            );

            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(country);
            given(couponService.findByCode(code)).willReturn(coupon);

            //when + then
            assertThrows(CouponExhaustedException.class,
                    () -> couponUseCase.use(command));
        }
    }
}
