package com.rzodeczko.infrastructure.geolocation.resilience;

import com.rzodeczko.application.exception.GeoLocationException;
import com.rzodeczko.application.exception.GeoLocationNetworkException;
import com.rzodeczko.application.exception.GeoLocationUnavailableException;
import com.rzodeczko.application.port.output.GeoLocationProvider;
import com.rzodeczko.domain.model.Country;
import com.rzodeczko.infrastructure.configuration.RetryConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SpringBootTest(
        classes = {
                ResilientGeoLocationAdapterIT.Config.class,
                RetryConfiguration.class
        },
        properties = {
                "resilience4j.circuitbreaker.instances.geolocation.sliding-window-type=COUNT_BASED",
                "resilience4j.circuitbreaker.instances.geolocation.sliding-window-size=5",
                "resilience4j.circuitbreaker.instances.geolocation.minimum-number-of-calls=3",
                "resilience4j.circuitbreaker.instances.geolocation.failure-rate-threshold=50",
                "resilience4j.circuitbreaker.instances.geolocation.wait-duration-in-open-state=60s",
                "resilience4j.circuitbreaker.instances.geolocation.permitted-number-of-calls-in-half-open-state=1",
                "resilience4j.circuitbreaker.instances.geolocation.record-exceptions=com.rzodeczko.application.exception.GeoLocationNetworkException",
                "resilience4j.circuitbreaker.circuit-breaker-aspect-order=3"
        }
)
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        LiquibaseAutoConfiguration.class
})
class ResilientGeoLocationAdapterIT {

    private static final String IP = "89.64.55.1";
    private static final Country PL = new Country("PL");

    /**
     * Shared mock - not a Spring bean, so no CGLIB wrapping.
     */
    static final GeoLocationProvider MOCK_DELEGATE = mock(GeoLocationProvider.class);

    @Autowired
    private GeoLocationProvider resilientAdapter;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetState() {
        reset(MOCK_DELEGATE);
        circuitBreakerRegistry.circuitBreaker("geolocation").reset();
    }

    @TestConfiguration
    static class Config {

        @Bean
        GeoLocationProvider resilientAdapter() {
            return new ResilientGeoLocationAdapter(MOCK_DELEGATE);
        }
    }

    @Nested
    @DisplayName("Retry behavior")
    class RetryBehavior {

        @Test
        @DisplayName("should retry up to 3 times on GeoLocationNetworkException then succeed")
        void shouldRetryAndSucceed() {
            given(MOCK_DELEGATE.resolveCountry(IP))
                    .willThrow(new GeoLocationNetworkException("Timeout", new RuntimeException()))
                    .willThrow(new GeoLocationNetworkException("Timeout", new RuntimeException()))
                    .willReturn(PL);

            Country result = resilientAdapter.resolveCountry(IP);

            assertThat(result).isEqualTo(PL);
            verify(MOCK_DELEGATE, times(3)).resolveCountry(IP);
        }

        @Test
        @DisplayName("should exhaust retries and throw after 3 consecutive network failures")
        void shouldExhaustRetriesAndThrow() {
            given(MOCK_DELEGATE.resolveCountry(IP))
                    .willThrow(new GeoLocationNetworkException("Timeout", new RuntimeException()));

            assertThatThrownBy(() -> resilientAdapter.resolveCountry(IP))
                    .isInstanceOf(GeoLocationNetworkException.class);

            verify(MOCK_DELEGATE, times(3)).resolveCountry(IP);
        }

        @Test
        @DisplayName("should NOT retry on non-retryable GeoLocationException")
        void shouldNotRetryOnNonRetryableException() {
            given(MOCK_DELEGATE.resolveCountry(IP))
                    .willThrow(new GeoLocationException("Non-public IP"));

            assertThatThrownBy(() -> resilientAdapter.resolveCountry(IP))
                    .isInstanceOf(GeoLocationException.class)
                    .hasMessage("Non-public IP");

            verify(MOCK_DELEGATE, times(1)).resolveCountry(IP);
        }
    }

    @Nested
    @DisplayName("Circuit breaker behavior")
    class CircuitBreakerBehavior {

        @Test
        @DisplayName("should throw GeoLocationUnavailableException when circuit breaker is open")
        void shouldRejectCallsWhenCircuitOpen() {
            given(MOCK_DELEGATE.resolveCountry(IP))
                    .willThrow(new GeoLocationNetworkException("Timeout", new RuntimeException()));

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("geolocation");

            // Drive circuit breaker to OPEN: need minimumNumberOfCalls (3) failures.
            // Each call retries 3 times internally, so 3 logical calls = 9 delegate invocations.
            for (int i = 0; i < 3; i++) {
                try {
                    resilientAdapter.resolveCountry(IP);
                } catch (GeoLocationNetworkException ignored) {
                    // expected - retries exhausted
                }
            }

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Next call should be rejected immediately by the circuit breaker
            assertThatThrownBy(() -> resilientAdapter.resolveCountry(IP))
                    .isInstanceOf(GeoLocationUnavailableException.class)
                    .hasMessageContaining("circuit breaker is open")
                    .hasMessageContaining(IP);
        }

        @Test
        @DisplayName("should keep circuit closed when failures are below threshold")
        void shouldKeepCircuitClosedBelowThreshold() {
            // With minimumNumberOfCalls=3, failureRateThreshold=50%:
            // 2 successes + 1 failure (after 3 retries) = 33% failure rate -> stays CLOSED.
            // The third logical call retries 3 times, so the mock must throw on calls 3-5.
            given(MOCK_DELEGATE.resolveCountry(IP))
                    .willReturn(PL)                // logical call 1 -> success
                    .willReturn(PL)                // logical call 2 -> success
                    .willThrow(new GeoLocationNetworkException("Timeout", new RuntimeException()))  // logical call 3, attempt 1
                    .willThrow(new GeoLocationNetworkException("Timeout", new RuntimeException()))  // logical call 3, attempt 2
                    .willThrow(new GeoLocationNetworkException("Timeout", new RuntimeException())); // logical call 3, attempt 3

            resilientAdapter.resolveCountry(IP);
            resilientAdapter.resolveCountry(IP);
            try {
                resilientAdapter.resolveCountry(IP);
            } catch (GeoLocationNetworkException ignored) {
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("geolocation");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("should not record non-network exceptions in circuit breaker")
        void shouldNotRecordNonNetworkExceptions() {
            // GeoLocationException (not network) should not count toward the failure rate
            given(MOCK_DELEGATE.resolveCountry(IP))
                    .willThrow(new GeoLocationException("Non-public IP"));

            for (int i = 0; i < 5; i++) {
                try {
                    resilientAdapter.resolveCountry(IP);
                } catch (GeoLocationException ignored) {
                }
            }

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("geolocation");
            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }
}
