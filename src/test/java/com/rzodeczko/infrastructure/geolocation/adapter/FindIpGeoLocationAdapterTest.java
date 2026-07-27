package com.rzodeczko.infrastructure.geolocation.adapter;

import com.rzodeczko.application.exception.GeoLocationException;
import com.rzodeczko.application.exception.GeoLocationNetworkException;
import com.rzodeczko.application.exception.VpnDetectedException;
import com.rzodeczko.domain.model.Country;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FindIpGeoLocationAdapterTest {

    private static final String TOKEN = "test-token";

    private MockRestServiceServer mockServer;
    private FindIpGeoLocationAdapter adapter;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new FindIpGeoLocationAdapter(builder, TOKEN);
    }

    @Nested
    class ResolveCountry {

        @Test
        void shouldReturnCountryOnSuccess() {
            mockServer.expect(requestTo("https://api.findip.net/89.64.55.1/?token=test-token"))
                    .andRespond(withSuccess("""
                            {
                              "country": {"iso_code": "PL"},
                              "intelligence": {
                                "flags": {
                                  "is_vpn": false,
                                  "is_proxy": false,
                                  "is_tor": false,
                                  "is_hosting": false
                                }
                              }
                            }
                            """, MediaType.APPLICATION_JSON));

            var country = adapter.resolveCountry("89.64.55.1");

            assertEquals(new Country("PL"), country);
            mockServer.verify();
        }

        @Test
        void shouldThrowVpnDetectedWhenVpnFlagTrue() {
            mockServer.expect(requestTo("https://api.findip.net/1.2.3.4/?token=test-token"))
                    .andRespond(withSuccess("""
                            {
                              "country": {"iso_code": "US"},
                              "intelligence": {
                                "flags": {
                                  "is_vpn": true,
                                  "is_proxy": false,
                                  "is_tor": false,
                                  "is_hosting": false
                                }
                              }
                            }
                            """, MediaType.APPLICATION_JSON));

            assertThrows(VpnDetectedException.class,
                    () -> adapter.resolveCountry("1.2.3.4"));
            mockServer.verify();
        }

        @Test
        void shouldThrowVpnDetectedWhenProxyFlagTrue() {
            mockServer.expect(requestTo("https://api.findip.net/1.2.3.4/?token=test-token"))
                    .andRespond(withSuccess("""
                            {
                              "country": {"iso_code": "US"},
                              "intelligence": {
                                "flags": {
                                  "is_vpn": false,
                                  "is_proxy": true,
                                  "is_tor": false,
                                  "is_hosting": false
                                }
                              }
                            }
                            """, MediaType.APPLICATION_JSON));

            assertThrows(VpnDetectedException.class,
                    () -> adapter.resolveCountry("1.2.3.4"));
            mockServer.verify();
        }

        @Test
        void shouldThrowVpnDetectedWhenTorFlagTrue() {
            mockServer.expect(requestTo("https://api.findip.net/1.2.3.4/?token=test-token"))
                    .andRespond(withSuccess("""
                            {
                              "country": {"iso_code": "US"},
                              "intelligence": {
                                "flags": {
                                  "is_vpn": false,
                                  "is_proxy": false,
                                  "is_tor": true,
                                  "is_hosting": false
                                }
                              }
                            }
                            """, MediaType.APPLICATION_JSON));

            assertThrows(VpnDetectedException.class,
                    () -> adapter.resolveCountry("1.2.3.4"));
            mockServer.verify();
        }

        @Test
        void shouldThrowVpnDetectedWhenHostingFlagTrue() {
            mockServer.expect(requestTo("https://api.findip.net/1.2.3.4/?token=test-token"))
                    .andRespond(withSuccess("""
                            {
                              "country": {"iso_code": "US"},
                              "intelligence": {
                                "flags": {
                                  "is_vpn": false,
                                  "is_proxy": false,
                                  "is_tor": false,
                                  "is_hosting": true
                                }
                              }
                            }
                            """, MediaType.APPLICATION_JSON));

            assertThrows(VpnDetectedException.class,
                    () -> adapter.resolveCountry("1.2.3.4"));
            mockServer.verify();
        }

        @Test
        void shouldThrowWhenResponseIsNull() {
            mockServer.expect(requestTo("https://api.findip.net/8.8.8.8/?token=test-token"))
                    .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

            assertThrows(GeoLocationException.class,
                    () -> adapter.resolveCountry("8.8.8.8"));
            mockServer.verify();
        }

        @Test
        void shouldThrowWhenCountryCodeIsBlank() {
            mockServer.expect(requestTo("https://api.findip.net/8.8.8.8/?token=test-token"))
                    .andRespond(withSuccess("""
                            {
                              "country": {"iso_code": ""},
                              "intelligence": {
                                "flags": {
                                  "is_vpn": false,
                                  "is_proxy": false,
                                  "is_tor": false,
                                  "is_hosting": false
                                }
                              }
                            }
                            """, MediaType.APPLICATION_JSON));

            assertThrows(GeoLocationException.class,
                    () -> adapter.resolveCountry("8.8.8.8"));
            mockServer.verify();
        }

        @Test
        void shouldThrowNetworkExceptionOnServerError() {
            mockServer.expect(requestTo("https://api.findip.net/8.8.8.8/?token=test-token"))
                    .andRespond(withServerError());

            assertThrows(GeoLocationNetworkException.class,
                    () -> adapter.resolveCountry("8.8.8.8"));
            mockServer.verify();
        }

        @Test
        void shouldThrowWhenIpAddressFormatIsInvalid() {
            assertThrows(GeoLocationException.class,
                    () -> adapter.resolveCountry("invalid"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"127.0.0.1", "0:0:0:0:0:0:0:1", "10.0.0.1", "192.168.1.1", "172.16.0.1"})
        void shouldRejectNonPublicIpAddresses(String privateIp) {
            var exception = assertThrows(GeoLocationException.class,
                    () -> adapter.resolveCountry(privateIp));

            assertTrue(exception.getMessage().contains("non-public"));
        }
    }
}
