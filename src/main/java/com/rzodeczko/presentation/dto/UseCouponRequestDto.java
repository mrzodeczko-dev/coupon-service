package com.rzodeczko.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record UseCouponRequestDto(
        @NotBlank(message = "User ID must not be blank")
        String userId
) {
}
