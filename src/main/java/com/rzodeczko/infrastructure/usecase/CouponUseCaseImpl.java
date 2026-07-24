package com.rzodeczko.infrastructure.usecase;

import com.rzodeczko.application.port.input.CreateCouponCommand;
import com.rzodeczko.application.port.input.CreateCouponUseCase;
import com.rzodeczko.application.port.input.UseCouponCommand;
import com.rzodeczko.application.port.input.UseCouponUseCase;
import com.rzodeczko.application.port.output.GeoLocationProvider;
import com.rzodeczko.application.service.CouponService;
import com.rzodeczko.domain.exception.CouponAlreadyUsedByUserException;
import com.rzodeczko.domain.exception.CouponConcurrentModificationException;
import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.domain.model.CouponCode;
import com.rzodeczko.domain.model.Country;
import com.rzodeczko.infrastructure.transaction.CouponTransactionBoundary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CouponUseCaseImpl implements CreateCouponUseCase, UseCouponUseCase {

    private final CouponTransactionBoundary couponTransactionBoundary;
    private final GeoLocationProvider geoLocationProvider;

    @Override
    public Coupon create(CreateCouponCommand command) {
        log.info("Creating coupon. code={}, country={}", command.code(), command.country());

        var code = new CouponCode(command.code());
        var country = new Country(command.country());

        Coupon saved = couponTransactionBoundary.save(code, command.maxUsages(), country);

        log.info("Coupon created. id={}, code={}", saved.getId(), saved.getCode().value());
        return saved;
    }

    @Override
    public Coupon use(UseCouponCommand command) {
        log.info("Using coupon. code={}, userId={}, ip={}", command.code(), command.userId(), command.ipAddress());

        var code = new CouponCode(command.code());
        couponTransactionBoundary.validateUserNotUsed(code, command.userId());

        Country requestCountry = geoLocationProvider.resolveCountry(command.ipAddress());

        Coupon saved = couponTransactionBoundary.executeUsage(code, command.userId(), requestCountry);

        log.info("Coupon used. code={}, userId={}, remainingUsages={}", saved.getCode().value(), command.userId(), saved.getRemainingUsages());
        return saved;
    }
}
