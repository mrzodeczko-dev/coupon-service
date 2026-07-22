package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.port.input.CreateCouponCommand;
import com.rzodeczko.application.port.input.CreateCouponUseCase;
import com.rzodeczko.application.port.input.UseCouponCommand;
import com.rzodeczko.application.port.input.UseCouponUseCase;
import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.presentation.dto.CreateCouponRequestDto;
import com.rzodeczko.presentation.dto.CreateCouponResponseDto;
import com.rzodeczko.presentation.dto.UseCouponResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing discount coupons.
 * <p>
 * Provides endpoints to create coupons and register coupon usage.
 * <ul>
 *   <li><b>POST /coupons</b> - Create a new coupon</li>
 *   <li><b>POST /coupons/{code}/usages</b> - Register coupon usage</li>
 * </ul>
 */
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
@Slf4j
public class CouponController {

    private final CreateCouponUseCase createCouponUseCase;
    private final UseCouponUseCase useCouponUseCase;

    @PostMapping
    public ResponseEntity<CreateCouponResponseDto> createCoupon(
            @RequestBody @Valid CreateCouponRequestDto request) {

        log.info(">>> Received create coupon request. code={}", request.code());

        var command = new CreateCouponCommand(
                request.code(),
                request.maxUsages(),
                request.country()
        );

        Coupon coupon = createCouponUseCase.create(command);

        var response = new CreateCouponResponseDto(
                coupon.getId(),
                coupon.getCode().value(),
                coupon.getMaxUsages(),
                coupon.getCountry().code()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{code}/usages")
    public ResponseEntity<UseCouponResponseDto> useCoupon(
            @PathVariable String code,
            HttpServletRequest request) {

        String ipAddress = resolveClientIp(request);
        log.info(">>> Received use coupon request. code={}, ip={}", code, ipAddress);

        var command = new UseCouponCommand(code, ipAddress);
        Coupon coupon = useCouponUseCase.use(command);

        var response = new UseCouponResponseDto(
                coupon.getCode().value(),
                coupon.getCurrentUsages(),
                coupon.getMaxUsages(),
                coupon.getRemainingUsages()
        );

        return ResponseEntity.ok(response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
