package com.rzodeczko.infrastructure.geolocation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from api.findip.net/{ip}/?token={token}
 * <p>
 * Relevant fields:
 * - country.iso_code (ISO 3166-1 alpha-2)
 * - intelligence.flags.is_vpn, is_proxy, is_tor, is_hosting
 */
public record FindIpGeoLocationResponse(
        Country country,
        Intelligence intelligence
) {

    public record Country(
            @JsonProperty("iso_code") String isoCode
    ) {}

    public record Intelligence(
            Flags flags
    ) {}

    public record Flags(
            @JsonProperty("is_vpn") boolean vpn,
            @JsonProperty("is_proxy") boolean proxy,
            @JsonProperty("is_tor") boolean tor,
            @JsonProperty("is_hosting") boolean hosting
    ) {
        public boolean isAnonymous() {
            return vpn || proxy || tor;
        }
    }
}
