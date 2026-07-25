package com.rzodeczko.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to create a new discount coupon")
public record CreateCouponRequestDto(
        @Schema(description = "Unique coupon code (case-insensitive, stored uppercase)", example = "SUMMER26", maxLength = 100)
        @NotBlank(message = "Coupon code must not be blank")
        @Size(max = 100, message = "Coupon code must be at most 100 characters")
        String code,

        @Schema(description = "Maximum number of times the coupon can be used across all users", example = "100", minimum = "1", maximum = "1000000")
        @Min(value = 1, message = "Max usages must be at least 1")
        @Max(value = 1_000_000, message = "Max usages must not exceed 1000000")
        int maxUsages,

        @Schema(description = "ISO 3166-1 alpha-2 country code restricting where the coupon can be used", example = "PL")
        @NotBlank(message = "Country must not be blank")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "Country must be a valid ISO 3166-1 alpha-2 code")
        String country
) {
}
