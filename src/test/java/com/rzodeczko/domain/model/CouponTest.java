package com.rzodeczko.domain.model;

import com.rzodeczko.domain.exception.CouponCountryMismatchException;
import com.rzodeczko.domain.exception.CouponExhaustedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CouponTest {

    private static final CouponCode CODE = new CouponCode("SUMMER2025");
    private static final Country PL = new Country("PL");
    private static final Country DE = new Country("DE");

    // --- create ---

    @Test
    void shouldCreateCouponWithZeroUsages() {
        var coupon = Coupon.create(CODE, 100, PL);

        assertEquals(CODE, coupon.getCode());
        assertEquals(0, coupon.getCurrentUsages());
        assertEquals(100, coupon.getMaxUsages());
        assertEquals(PL, coupon.getCountry());
        assertNotNull(coupon.getId());
        assertNotNull(coupon.getCreatedAt());
    }

    @Test
    void shouldRejectZeroMaxUsages() {
        assertThrows(IllegalArgumentException.class, () -> Coupon.create(CODE, 0, PL));
    }

    @Test
    void shouldRejectNegativeMaxUsages() {
        assertThrows(IllegalArgumentException.class, () -> Coupon.create(CODE, -5, PL));
    }

    // --- use ---

    @Test
    void shouldIncrementUsagesOnUse() {
        var coupon = Coupon.create(CODE, 10, PL);

        coupon.use(PL);

        assertEquals(1, coupon.getCurrentUsages());
        assertEquals(9, coupon.getRemainingUsages());
    }

    @Test
    void shouldAllowMultipleUsagesUpToLimit() {
        var coupon = Coupon.create(CODE, 3, PL);

        coupon.use(PL);
        coupon.use(PL);
        coupon.use(PL);

        assertTrue(coupon.isExhausted());
        assertEquals(0, coupon.getRemainingUsages());
    }

    @Test
    void shouldThrowWhenCouponExhausted() {
        var coupon = Coupon.create(CODE, 1, PL);
        coupon.use(PL);

        assertThrows(CouponExhaustedException.class, () -> coupon.use(PL));
    }

    @Test
    void shouldThrowWhenCountryDoesNotMatch() {
        var coupon = Coupon.create(CODE, 10, PL);

        assertThrows(CouponCountryMismatchException.class, () -> coupon.use(DE));
    }

    @Test
    void shouldNotIncrementUsagesOnCountryMismatch() {
        var coupon = Coupon.create(CODE, 10, PL);

        assertThrows(CouponCountryMismatchException.class, () -> coupon.use(DE));
        assertEquals(0, coupon.getCurrentUsages());
    }

    @Test
    void shouldCheckExhaustedBeforeCountry() {
        var coupon = Coupon.create(CODE, 1, PL);
        coupon.use(PL);

        // exhausted takes precedence over country mismatch
        assertThrows(CouponExhaustedException.class, () -> coupon.use(DE));
    }

    // --- reconstitute ---

    @Test
    void shouldReconstituteCouponFromPersistedState() {
        var original = Coupon.create(CODE, 5, PL);
        original.use(PL);
        original.use(PL);

        var restored = Coupon.reconstitute(
                original.getId(), original.getCode(), original.getCreatedAt(),
                original.getMaxUsages(), original.getCurrentUsages(), original.getCountry()
        );

        assertEquals(original.getId(), restored.getId());
        assertEquals(2, restored.getCurrentUsages());
        assertEquals(original, restored);
    }
}
