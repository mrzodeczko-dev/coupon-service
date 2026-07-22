package com.rzodeczko.infrastructure.geolocation.dto;

/**
 * Response from ip-api.com/json/{ip}?fields=status,countryCode,message
 */
public record GeoLocationResponse(
        String status,
        String countryCode,
        String message
) {
    public boolean isSuccess() {
        return "success".equals(status);
    }
}
