package com.rzodeczko.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request to create a new discount coupon")
public record CreateCouponRequestDto(
        @Schema(description = "Unique coupon code (case-insensitive, stored uppercase)", example = "SUMMER25")
        @NotBlank(message = "Coupon code must not be blank")
        String code,

        @Schema(description = "Maximum number of times the coupon can be used across all users", example = "100", minimum = "1")
        @Min(value = 1, message = "Max usages must be at least 1")
        int maxUsages,

        @Schema(description = "ISO 3166-1 alpha-2 country code restricting where the coupon can be used", example = "PL")
        @NotBlank(message = "Country must not be blank")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "Country must be a valid ISO 3166-1 alpha-2 code")
        String country
) {
}
