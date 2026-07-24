package com.rzodeczko.application.exception;

/**
 * Thrown on transient network errors (timeout, connection refused).
 * Eligible for retry, unlike logical failures (invalid IP, API "fail" status).
 */
public class GeoLocationNetworkException extends GeoLocationException {

    public GeoLocationNetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
