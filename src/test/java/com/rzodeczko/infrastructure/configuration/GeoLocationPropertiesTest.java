package com.rzodeczko.infrastructure.configuration;

import com.rzodeczko.infrastructure.configuration.GeoLocationProperties.FindIp;
import com.rzodeczko.infrastructure.configuration.GeoLocationProperties.Provider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

class GeoLocationPropertiesTest {

    @Nested
    class FindIpTokenValidation {

        @Test
        void shouldReturnTokenWhenSet() {
            var findIp = new FindIp("my-token");

            assertEquals("my-token", findIp.requireToken());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        void shouldThrowWhenTokenMissingOrBlank(String token) {
            var findIp = new FindIp(token);

            var ex = assertThrows(IllegalStateException.class, findIp::requireToken);
            assertTrue(ex.getMessage().contains("geolocation.findip.token must be set"));
        }
    }

    @Nested
    class RecordDefaults {

        @Test
        void shouldDefaultFindIpWhenNull() {
            var props = new GeoLocationProperties(Provider.IP_API, null);

            assertNotNull(props.findip());
            assertNull(props.findip().token());
        }

        @Test
        void shouldPreserveFindIpWhenProvided() {
            var findIp = new FindIp("token-123");
            var props = new GeoLocationProperties(Provider.FINDIP, findIp);

            assertEquals("token-123", props.findip().token());
        }
    }

    @Nested
    class PropertyBinding {

        @Nested
        @SpringBootTest(classes = PropertyBinding.IpApiBinding.Config.class)
        @TestPropertySource(properties = "geolocation.provider=ip-api")
        class IpApiBinding {

            @EnableConfigurationProperties(GeoLocationProperties.class)
            static class Config {
            }

            @Autowired
            private GeoLocationProperties properties;

            @Test
            void shouldBindIpApiProvider() {
                assertEquals(Provider.IP_API, properties.provider());
            }
        }

        @Nested
        @SpringBootTest(classes = PropertyBindingFindIp.Config.class)
        @TestPropertySource(properties = {
                "geolocation.provider=findip",
                "geolocation.findip.token=test-token"
        })
        class PropertyBindingFindIp {

            @EnableConfigurationProperties(GeoLocationProperties.class)
            static class Config {
            }

            @Autowired
            private GeoLocationProperties properties;

            @Test
            void shouldBindFindIpProviderWithToken() {
                assertEquals(Provider.FINDIP, properties.provider());
                assertEquals("test-token", properties.findip().requireToken());
            }
        }
    }
}
