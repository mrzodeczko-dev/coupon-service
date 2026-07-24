package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.port.input.CreateCouponCommand;
import com.rzodeczko.application.port.input.CreateCouponUseCase;
import com.rzodeczko.application.port.input.UseCouponCommand;
import com.rzodeczko.application.port.input.UseCouponUseCase;
import com.rzodeczko.domain.model.Coupon;
import com.rzodeczko.presentation.dto.CreateCouponRequestDto;
import com.rzodeczko.presentation.dto.CreateCouponResponseDto;
import com.rzodeczko.presentation.dto.UseCouponRequestDto;
import com.rzodeczko.presentation.dto.UseCouponResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Coupons", description = "Endpoints for creating and using discount coupons")
public class CouponController {

    private final CreateCouponUseCase createCouponUseCase;
    private final UseCouponUseCase useCouponUseCase;

    @Operation(summary = "Create a new coupon", description = "Creates a coupon with a unique code, usage limit, and country restriction")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Coupon created successfully",
                    content = @Content(schema = @Schema(implementation = CreateCouponResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body (blank code, invalid country format, maxUsages < 1)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Coupon with this code already exists",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
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

    @Operation(summary = "Use a coupon", description = "Registers a coupon usage for a given user. "
            + "The caller's country is resolved from the IP address and must match the coupon's country restriction.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Coupon used successfully",
                    content = @Content(schema = @Schema(implementation = UseCouponResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body (blank userId)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Caller's country does not match the coupon's country restriction",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Coupon with the given code not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Coupon exhausted, already used by this user, or concurrent modification",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Geolocation service temporarily unavailable",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{code}/usages")
    public ResponseEntity<UseCouponResponseDto> useCoupon(
            @Parameter(description = "Coupon code", example = "SUMMER26")
            @PathVariable String code,
            @RequestBody @Valid UseCouponRequestDto useCouponRequest,
            HttpServletRequest request) {

        String ipAddress = resolveClientIp(request);
        log.info(">>> Received use coupon request. code={}, userId={}, ip={}", code, useCouponRequest.userId(), ipAddress);

        var command = new UseCouponCommand(code, useCouponRequest.userId(), ipAddress);
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
