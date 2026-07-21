package com.rzodeczko.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CouponCodeTest {

    @Test
    void shouldNormalizeToUpperCase() {
        var code = new CouponCode("wiosna");

        assertEquals("WIOSNA", code.value());
    }

    @Test
    void shouldTreatDifferentCasesAsEqual() {
        var lower = new CouponCode("spring");
        var upper = new CouponCode("SPRING");

        assertEquals(lower, upper);
    }

    @Test
    void shouldRejectBlankCode() {
        assertThrows(IllegalArgumentException.class, () -> new CouponCode("  "));
    }

    @Test
    void shouldRejectNullCode() {
        assertThrows(IllegalArgumentException.class, () -> new CouponCode(null));
    }
}
