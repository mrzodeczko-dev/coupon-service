package com.rzodeczko.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response after successfully using a coupon")
public record UseCouponResponseDto(
        @Schema(description = "Coupon code", example = "SUMMER25")
        String code,

        @Schema(description = "Total usages so far", example = "1")
        int currentUsages,

        @Schema(description = "Maximum allowed usages", example = "100")
        int maxUsages,

        @Schema(description = "Remaining usages before coupon is exhausted", example = "99")
        int remainingUsages
) {
}
