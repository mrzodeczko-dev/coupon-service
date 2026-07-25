package com.rzodeczko.infrastructure.geolocation.resilience;

import com.rzodeczko.application.exception.GeoLocationException;
import com.rzodeczko.application.exception.GeoLocationNetworkException;
import com.rzodeczko.application.exception.GeoLocationUnavailableException;
import com.rzodeczko.application.port.output.GeoLocationProvider;
import com.rzodeczko.domain.model.Country;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ResilientGeoLocationAdapter}.
 * Tests delegation and fallback logic directly (without Spring proxy).
 * Retry and circuit breaker integration is covered by {@link ResilientGeoLocationAdapterIT}.
 */
@ExtendWith(MockitoExtension.class)
class ResilientGeoLocationAdapterTest {

    @Mock
    private GeoLocationProvider delegate;

    private ResilientGeoLocationAdapter adapter;

    private static final String IP = "89.64.55.1";
    private static final Country PL = new Country("PL");

    @BeforeEach
    void setUp() {
        adapter = new ResilientGeoLocationAdapter(delegate);
    }

    @Nested
    @DisplayName("Delegation")
    class Delegation {

        @Test
        @DisplayName("should delegate to underlying provider on success")
        void shouldDelegateToUnderlyingProvider() {
            given(delegate.resolveCountry(IP)).willReturn(PL);

            Country result = adapter.resolveCountry(IP);

            assertThat(result).isEqualTo(PL);
            verify(delegate).resolveCountry(IP);
        }

        @Test
        @DisplayName("should propagate GeoLocationException from delegate")
        void shouldPropagateGeoLocationException() {
            given(delegate.resolveCountry(IP))
                    .willThrow(new GeoLocationException("Invalid IP"));

            assertThatThrownBy(() -> adapter.resolveCountry(IP))
                    .isInstanceOf(GeoLocationException.class)
                    .hasMessage("Invalid IP");
        }

        @Test
        @DisplayName("should propagate GeoLocationNetworkException from delegate")
        void shouldPropagateNetworkException() {
            given(delegate.resolveCountry(IP))
                    .willThrow(new GeoLocationNetworkException("Timeout", new RuntimeException()));

            assertThatThrownBy(() -> adapter.resolveCountry(IP))
                    .isInstanceOf(GeoLocationNetworkException.class)
                    .hasMessageContaining("Timeout");
        }
    }

    @Nested
    @DisplayName("Fallback")
    class Fallback {

        @Test
        @DisplayName("should throw GeoLocationUnavailableException with IP in message")
        void shouldThrowUnavailableExceptionWithIp() {
            CircuitBreaker cb = CircuitBreaker.of("test", CircuitBreakerConfig.ofDefaults());
            var callNotPermitted = CallNotPermittedException.createCallNotPermittedException(cb);

            assertThatThrownBy(() -> adapter.fallback(IP, callNotPermitted))
                    .isInstanceOf(GeoLocationUnavailableException.class)
                    .hasMessageContaining(IP)
                    .hasMessageContaining("circuit breaker is open");
        }
    }
}
