package com.rzodeczko.domain.model;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record Country(String code) {

    private static final Pattern ISO_ALPHA_2 = Pattern.compile("^[A-Z]{2}$");

    /**
     * Set of valid ISO 3166-1 alpha-2 country codes as recognized by the JVM.
     */
    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    public Country {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Country code must not be blank");
        }
        code = code.toUpperCase(Locale.ROOT);
        if (!ISO_ALPHA_2.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "Country code must be ISO 3166-1 alpha-2 format (e.g. PL, US, DE), got: " + code
            );
        }
        if (!ISO_COUNTRIES.contains(code)) {
            throw new IllegalArgumentException(
                    "Country code is not a recognized ISO 3166-1 alpha-2 code: " + code
            );
        }
    }
}
