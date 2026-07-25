package com.rzodeczko.infrastructure.geolocation.resilience;

import com.rzodeczko.application.exception.GeoLocationNetworkException;
import com.rzodeczko.application.exception.GeoLocationUnavailableException;
import com.rzodeczko.application.port.output.GeoLocationProvider;
import com.rzodeczko.domain.model.Country;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

@RequiredArgsConstructor
public class ResilientGeoLocationAdapter implements GeoLocationProvider {

    private final GeoLocationProvider delegate;

    @Override
    @CircuitBreaker(name = "geolocation", fallbackMethod = "fallback")
    @Retryable(
            retryFor = GeoLocationNetworkException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50))
    public Country resolveCountry(String ipAddress) {
        return delegate.resolveCountry(ipAddress);
    }

    Country fallback(String ipAddress, CallNotPermittedException e) {
        throw new GeoLocationUnavailableException(
                "Geolocation service circuit breaker is open, request rejected for IP: %s".formatted(ipAddress)
        );
    }
}
