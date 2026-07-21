package com.rzodeczko.application.port.output;

import com.rzodeczko.domain.model.Country;

/**
 * Outbound port for resolving a country from an IP address.
 * Implemented by infrastructure adapters
 */
public interface GeoLocationProvider {

    Country resolveCountry(String ipAddress);
}
