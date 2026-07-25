package com.rzodeczko.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CountryTest {

    @Test
    void shouldNormalizeToUpperCase() {
        var country = new Country("pl");

        assertEquals("PL", country.code());
    }

    @Test
    void shouldAcceptValidIsoCode() {
        assertDoesNotThrow(() -> new Country("US"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PL", "US", "DE", "GB", "FR", "JP"})
    void shouldAcceptRealIsoCountries(String code) {
        assertDoesNotThrow(() -> new Country(code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"P", "POL", "1A", "P!", ""})
    void shouldRejectInvalidFormat(String code) {
        assertThrows(IllegalArgumentException.class, () -> new Country(code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"XX", "ZZ", "AA", "QQ", "OO"})
    void shouldRejectSyntacticallyValidButUnknownIsoCode(String code) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Country(code));
        assertTrue(ex.getMessage().contains("not a recognized ISO 3166-1"),
                "Expected ISO-lookup rejection message, got: " + ex.getMessage());
    }

    @Test
    void shouldRejectNull() {
        assertThrows(IllegalArgumentException.class, () -> new Country(null));
    }
}
