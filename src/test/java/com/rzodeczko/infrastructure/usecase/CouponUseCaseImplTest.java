package com.rzodeczko.infrastructure.usecase;

import com.rzodeczko.application.port.input.CreateCouponCommand;
import com.rzodeczko.application.port.input.UseCouponCommand;
import com.rzodeczko.application.port.output.GeoLocationProvider;
import com.rzodeczko.domain.exception.CouponAlreadyExistsException;
import com.rzodeczko.domain.exception.CouponAlreadyUsedByUserException;
import com.rzodeczko.domain.exception.CouponConcurrentModificationException;
import com.rzodeczko.domain.exception.CouponCountryMismatchException;
import com.rzodeczko.domain.exception.CouponExhaustedException;
import com.rzodeczko.domain.exception.CouponNotFoundException;
import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.domain.model.CouponCode;
import com.rzodeczko.domain.model.Country;
import com.rzodeczko.infrastructure.transaction.CouponTransactionBoundary;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CouponUseCaseImplTest {

    @Mock
    private CouponTransactionBoundary couponTransactionBoundary;

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
            var savedCoupon = Coupon.create(code, 100, country);

            given(couponTransactionBoundary.save(code, 100, country)).willReturn(savedCoupon);

            //when:
            Coupon result = couponUseCase.create(command);

            //then:
            assertNotNull(result);
            assertEquals("SUMMER25", result.getCode().value());
            assertEquals("PL", result.getCountry().code());
            then(couponTransactionBoundary).should().save(code, 100, country);
        }

        @Test
        void shouldPropagateExceptionWhenCodeAlreadyExists() {
            //given:
            var command = new CreateCouponCommand("SUMMER25", 100, "PL");

            given(couponTransactionBoundary.save(any(), anyInt(), any()))
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
            coupon.use(country);

            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(country);
            given(couponTransactionBoundary.executeUsage(code, "user-1", country)).willReturn(coupon);

            //when:
            Coupon result = couponUseCase.use(command);

            //then:
            assertEquals(1, result.getCurrentUsages());
            assertEquals(9, result.getRemainingUsages());
            then(couponTransactionBoundary).should().validateUserNotUsed(code, "user-1");
            then(geoLocationProvider).should().resolveCountry("89.64.55.1");
            then(couponTransactionBoundary).should().executeUsage(code, "user-1", country);
        }

        @Test
        void shouldThrowWhenUserAlreadyUsedCoupon() {
            //given:
            var command = new UseCouponCommand("SUMMER25", "user-1", "89.64.55.1");
            var code = new CouponCode("SUMMER25");

            willThrow(new CouponAlreadyUsedByUserException("SUMMER25", "user-1"))
                    .given(couponTransactionBoundary).validateUserNotUsed(code, "user-1");

            //when + then
            assertThrows(CouponAlreadyUsedByUserException.class,
                    () -> couponUseCase.use(command));

            then(geoLocationProvider).shouldHaveNoInteractions();
        }

        @Test
        void shouldThrowWhenCouponNotFound() {
            //given:
            var command = new UseCouponCommand("UNKNOWN", "user-1", "89.64.55.1");
            var code = new CouponCode("UNKNOWN");
            var country = new Country("PL");

            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(country);
            given(couponTransactionBoundary.executeUsage(code, "user-1", country))
                    .willThrow(new CouponNotFoundException("UNKNOWN"));

            //when + then
            assertThrows(CouponNotFoundException.class,
                    () -> couponUseCase.use(command));
        }

        @Test
        void shouldThrowWhenCountryMismatch() {
            //given:
            var command = new UseCouponCommand("SUMMER25", "user-1", "8.8.8.8");
            var code = new CouponCode("SUMMER25");
            var usCountry = new Country("US");

            given(geoLocationProvider.resolveCountry("8.8.8.8")).willReturn(usCountry);
            given(couponTransactionBoundary.executeUsage(code, "user-1", usCountry))
                    .willThrow(new CouponCountryMismatchException("PL", "US"));

            //when + then
            assertThrows(CouponCountryMismatchException.class,
                    () -> couponUseCase.use(command));
        }

        @Test
        void shouldThrowConcurrentModificationWhenOptimisticLockFails() {
            //given:
            var command = new UseCouponCommand("SUMMER25", "user-1", "89.64.55.1");
            var code = new CouponCode("SUMMER25");
            var country = new Country("PL");

            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(country);
            given(couponTransactionBoundary.executeUsage(code, "user-1", country))
                    .willThrow(new CouponConcurrentModificationException("SUMMER25"));

            //when + then
            assertThrows(CouponConcurrentModificationException.class,
                    () -> couponUseCase.use(command));
        }

        @Test
        void shouldThrowAlreadyUsedWhenDuplicateUsageRaceCondition() {
            //given:
            var command = new UseCouponCommand("SUMMER25", "user-1", "89.64.55.1");
            var code = new CouponCode("SUMMER25");
            var country = new Country("PL");

            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(country);
            given(couponTransactionBoundary.executeUsage(code, "user-1", country))
                    .willThrow(new CouponAlreadyUsedByUserException("SUMMER25", "user-1"));

            //when + then
            assertThrows(CouponAlreadyUsedByUserException.class,
                    () -> couponUseCase.use(command));
        }

        @Test
        void shouldThrowWhenCouponExhausted() {
            //given:
            var command = new UseCouponCommand("SUMMER25", "user-1", "89.64.55.1");
            var code = new CouponCode("SUMMER25");
            var country = new Country("PL");

            given(geoLocationProvider.resolveCountry("89.64.55.1")).willReturn(country);
            given(couponTransactionBoundary.executeUsage(code, "user-1", country))
                    .willThrow(new CouponExhaustedException("SUMMER25"));

            //when + then
            assertThrows(CouponExhaustedException.class,
                    () -> couponUseCase.use(command));
        }
    }
}
