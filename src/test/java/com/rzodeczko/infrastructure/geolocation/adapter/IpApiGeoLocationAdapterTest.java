package com.rzodeczko.infrastructure.geolocation.adapter;

import com.rzodeczko.application.exception.GeoLocationException;
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

class IpApiGeoLocationAdapterTest {

    private MockRestServiceServer mockServer;
    private IpApiGeoLocationAdapter adapter;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new IpApiGeoLocationAdapter(builder);
    }

    @Nested
    class ResolveCountry {

        @Test
        void shouldReturnCountryOnSuccess() {
            mockServer.expect(requestTo("http://ip-api.com/json/89.64.55.1?fields=status,countryCode,message,proxy,hosting"))
                    .andRespond(withSuccess("""
                            {"status":"success","countryCode":"PL", "hosting":false, "proxy":false}
                            """, MediaType.APPLICATION_JSON));

            var country = adapter.resolveCountry("89.64.55.1");

            assertEquals(new Country("PL"), country);
            mockServer.verify();
        }

        @Test
        void shouldThrowWhenApiReturnsFailStatus() {
            mockServer.expect(requestTo(("http://ip-api.com/json/1.2.3.4?fields=status,countryCode,message,proxy,hosting")))
                    .andRespond(withSuccess("""
                            {"status":"fail","message":"invalid query"}
                            """, MediaType.APPLICATION_JSON));

            assertThrows(GeoLocationException.class,
                    () -> adapter.resolveCountry("1.2.3.4"));
            mockServer.verify();
        }

        @Test
        void shouldThrowWhenIpAddressFormatIsInvalid() {
            assertThrows(GeoLocationException.class,
                    () -> adapter.resolveCountry("invalid"));
        }

        @Test
        void shouldThrowOnServerError() {
            mockServer.expect(requestTo("http://ip-api.com/json/8.8.8.8?fields=status,countryCode,message,proxy,hosting"))
                    .andRespond(withServerError());

            assertThrows(GeoLocationException.class,
                    () -> adapter.resolveCountry("8.8.8.8"));
            mockServer.verify();
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
