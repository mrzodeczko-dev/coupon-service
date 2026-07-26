package com.rzodeczko.infrastructure.geolocation.dto;

/**
 * Response from ip-api.com/json/{ip}?fields=status,countryCode,message,proxy,hosting
 */
public record GeoLocationResponse(
        String status,
        String countryCode,
        String message,
        boolean proxy,
        boolean hosting
) {
    public boolean isSuccess() {
        return "success".equals(status);
    }
}
