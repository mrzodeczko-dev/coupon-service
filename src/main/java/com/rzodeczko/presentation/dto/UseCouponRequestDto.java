package com.rzodeczko.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to register a coupon usage")
public record UseCouponRequestDto(
        @Schema(description = "ID of the user redeeming the coupon", example = "user-1")
        @NotBlank(message = "User ID must not be blank")
        String userId
) {
}
