package com.rzodeczko.application.exception;

/**
 * Thrown when the geolocation service is known to be unavailable
 * (e.g. circuit breaker is open). Maps to HTTP 503.
 *
 * <p>Not a subclass of {@link GeoLocationNetworkException}, so it does not
 * trigger retry and is not recorded by the circuit breaker.
 */
public class GeoLocationUnavailableException extends GeoLocationException {

    public GeoLocationUnavailableException(String message) {
        super(message);
    }
}
