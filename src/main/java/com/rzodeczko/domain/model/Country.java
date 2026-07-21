package com.rzodeczko.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

public record Country(String code) {

    private static final Pattern ISO_ALPHA_2 = Pattern.compile("^[A-Z]{2}$");

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
    }
}
