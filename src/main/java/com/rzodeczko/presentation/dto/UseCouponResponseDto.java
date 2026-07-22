package com.rzodeczko.presentation.dto;

public record UseCouponResponseDto(
        String code,
        int currentUsages,
        int maxUsages,
        int remainingUsages
) {
}
