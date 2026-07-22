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

    private final CouponService couponService;
    private final GeoLocationProvider geoLocationProvider;

    @Override
    @Transactional
    public Coupon create(CreateCouponCommand command) {
        log.info("Creating coupon. code={}, country={}", command.code(), command.country());

        var code = new CouponCode(command.code());
        var country = new Country(command.country());

        Coupon coupon = couponService.buildCoupon(code, command.maxUsages(), country);
        Coupon saved = couponService.save(coupon);

        log.info("Coupon created. id={}, code={}", saved.getId(), saved.getCode().value());
        return saved;
    }

    @Override
    @Transactional
    public Coupon use(UseCouponCommand command) {
        log.info("Using coupon. code={}, userId={}, ip={}", command.code(), command.userId(), command.ipAddress());

        var code = new CouponCode(command.code());

        couponService.validateUserNotUsed(code, command.userId());

        Country requestCountry = geoLocationProvider.resolveCountry(command.ipAddress());

        Coupon coupon = couponService.findByCode(code);
        coupon.use(requestCountry);

        Coupon saved;
        try {
            saved = couponService.save(coupon);
        } catch (OptimisticLockingFailureException e) {
            throw new CouponConcurrentModificationException(command.code());
        }

        try {
            couponService.recordUsage(code, command.userId());
        } catch (DataIntegrityViolationException e) {
            throw new CouponAlreadyUsedByUserException(command.code(), command.userId());
        }

        log.info("Coupon used. code={}, userId={}, remainingUsages={}", saved.getCode().value(), command.userId(), saved.getRemainingUsages());
        return saved;
    }
}
