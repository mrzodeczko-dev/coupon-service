package com.rzodeczko.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateCouponRequestDto(
        @NotBlank(message = "Coupon code must not be blank")
        String code,

        @Min(value = 1, message = "Max usages must be at least 1")
        int maxUsages,

        @NotBlank(message = "Country must not be blank")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "Country must be a valid ISO 3166-1 alpha-2 code")
        String country
) {
}
