package com.rzodeczko.infrastructure.geolocation.adapter;

import com.rzodeczko.application.exception.GeoLocationException;
import com.rzodeczko.application.exception.GeoLocationNetworkException;
import com.rzodeczko.application.port.output.GeoLocationProvider;
import com.rzodeczko.domain.model.Country;
import com.rzodeczko.infrastructure.geolocation.dto.GeoLocationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Objects;

@Component
@Slf4j
public class GeoLocationAdapter implements GeoLocationProvider {

    private static final String IP_API_BASE_URL = "http://ip-api.com";

    private final RestClient restClient;

    public GeoLocationAdapter(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(IP_API_BASE_URL)
                .build();
    }

    @Override
    public Country resolveCountry(String ipAddress) {
        log.debug("Resolving country for IP: {}", ipAddress);

        try {
            var response = restClient.get()
                    .uri("/json/{ip}?fields=status,countryCode,message", ipAddress)
                    .retrieve()
                    .body(GeoLocationResponse.class);

            if (Objects.isNull(response)) {
                throw new GeoLocationException(
                        "Empty response from geolocation service for IP: %s".formatted(ipAddress)
                );
            }

            if (!response.isSuccess()) {
                throw new GeoLocationException(
                        "Geolocation lookup failed for IP: %s, reason: %s"
                                .formatted(ipAddress, response.message())
                );
            }

            log.debug("Resolved IP {} to country {}", ipAddress, response.countryCode());
            return new Country(response.countryCode());

        } catch (ResourceAccessException e) {
            throw new GeoLocationNetworkException(
                    "Timeout/connection error resolving country for IP: %s".formatted(ipAddress), e
            );
        } catch (RestClientException e) {
            throw new GeoLocationNetworkException(
                    "Rest client error resolving country for IP: %s".formatted(ipAddress), e
            );
        }
    }
}
