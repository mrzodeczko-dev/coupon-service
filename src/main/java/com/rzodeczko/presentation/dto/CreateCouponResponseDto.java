package com.rzodeczko.presentation.dto;

import java.util.UUID;

public record CreateCouponResponseDto(
        UUID id,
        String code,
        int maxUsages,
        String country
) {
}
