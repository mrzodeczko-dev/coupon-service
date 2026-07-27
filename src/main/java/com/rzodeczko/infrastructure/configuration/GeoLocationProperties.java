package com.rzodeczko.infrastructure.configuration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "geolocation")
public record GeoLocationProperties(
        @NotNull Provider provider,
        FindIp findip
) {

    public GeoLocationProperties {
        if (findip == null) {
            findip = new FindIp(null);
        }
    }

    public enum Provider {
        IP_API,
        FINDIP
    }

    public record FindIp(String token) {

        public String requireToken() {
            if (token == null || token.isBlank()) {
                throw new IllegalStateException(
                        "geolocation.findip.token must be set when geolocation.provider=findip"
                );
            }
            return token;
        }
    }
}
