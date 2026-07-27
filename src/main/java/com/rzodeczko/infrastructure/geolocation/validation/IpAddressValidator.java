package com.rzodeczko.infrastructure.geolocation.validation;

import com.rzodeczko.application.exception.GeoLocationException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.InetAddress;
import java.net.UnknownHostException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IpAddressValidator {

    public static void requirePublicIp(String ipAddress) {
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                throw new GeoLocationException(
                        "Cannot resolve country for non-public IP address: %s".formatted(ipAddress)
                );
            }
        } catch (UnknownHostException e) {
            throw new GeoLocationException(
                    "Invalid IP address format: %s".formatted(ipAddress), e
            );
        }
    }
}
