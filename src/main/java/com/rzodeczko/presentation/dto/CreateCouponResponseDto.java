package com.rzodeczko.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Response after successfully creating a coupon")
public record CreateCouponResponseDto(
        @Schema(description = "Generated coupon ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Normalized coupon code (uppercase)", example = "SUMMER25")
        String code,

        @Schema(description = "Maximum allowed usages", example = "100")
        int maxUsages,

        @Schema(description = "Country restriction (ISO 3166-1 alpha-2)", example = "PL")
        String country
) {
}
