package com.rzodeczko.application.exception;

public class GeoLocationException extends RuntimeException {

    public GeoLocationException(String message) {
        super(message);
    }

    public GeoLocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
