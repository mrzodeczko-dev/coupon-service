package com.rzodeczko.infrastructure.geolocation.adapter;

import com.rzodeczko.application.exception.GeoLocationException;
import com.rzodeczko.application.exception.GeoLocationNetworkException;
import com.rzodeczko.application.exception.VpnDetectedException;
import com.rzodeczko.application.port.output.GeoLocationProvider;
import com.rzodeczko.domain.model.Country;
import com.rzodeczko.infrastructure.geolocation.dto.FindIpGeoLocationResponse;
import com.rzodeczko.infrastructure.geolocation.validation.IpAddressValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Objects;

@Slf4j
public class FindIpGeoLocationAdapter implements GeoLocationProvider {

    private static final String FIND_IP_BASE_URL = "https://api.findip.net";

    private final RestClient restClient;
    private final String token;

    public FindIpGeoLocationAdapter(RestClient.Builder restClientBuilder, String token) {
        this.restClient = restClientBuilder
                .baseUrl(FIND_IP_BASE_URL)
                .build();
        this.token = token;
    }

    @Override
    public Country resolveCountry(String ipAddress) {
        log.debug("Resolving country for IP via FindIP: {}", ipAddress);
        IpAddressValidator.requirePublicIp(ipAddress);

        try {
            var response = restClient.get()
                    .uri("/{ip}/?token={token}", ipAddress, token)
                    .retrieve()
                    .body(FindIpGeoLocationResponse.class);

            if (Objects.isNull(response) || Objects.isNull(response.country())) {
                throw new GeoLocationException(
                        "Empty response from FindIP for IP: %s".formatted(ipAddress)
                );
            }

            if (Objects.isNull(response.country().isoCode()) || response.country().isoCode().isBlank()) {
                throw new GeoLocationException(
                        "FindIP returned no country code for IP: %s".formatted(ipAddress)
                );
            }

            if (response.intelligence() != null && response.intelligence().flags() != null) {
                var flags = response.intelligence().flags();
                if (flags.isAnonymous() || flags.hosting()) {
                    throw new VpnDetectedException(
                            "Geolocation lookup failed for IP: %s, reason: VPN/proxy detected".formatted(ipAddress)
                    );
                }
            }

            log.debug("Resolved IP {} to country {} via FindIP", ipAddress, response.country().isoCode());
            return new Country(response.country().isoCode());

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
