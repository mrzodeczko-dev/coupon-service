package com.rzodeczko.domain.model;

import com.rzodeczko.domain.exception.CouponCountryMismatchException;
import com.rzodeczko.domain.exception.CouponExhaustedException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Coupon {

    private final UUID id;
    private final CouponCode code;
    private final Instant createdAt;
    private final int maxUsages;
    private int currentUsages;
    private final Country country;

    private Coupon(UUID id, CouponCode code, Instant createdAt, int maxUsages, int currentUsages, Country country) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.maxUsages = maxUsages;
        this.currentUsages = currentUsages;
        this.country = Objects.requireNonNull(country, "country must not be null");
    }

    /**
     * Factory method for creating a brand-new coupon.
     */
    public static Coupon create(CouponCode code, int maxUsages, Country country) {
        if (maxUsages <= 0) {
            throw new IllegalArgumentException("Max usages must be a positive number, got: " + maxUsages);
        }
        return new Coupon(UUID.randomUUID(), code, Instant.now(), maxUsages, 0, country);
    }

    /**
     * Reconstitution constructor - used by persistence adapters to rebuild the domain object.
     */
    public static Coupon reconstitute(UUID id, CouponCode code, Instant createdAt,
                                      int maxUsages, int currentUsages, Country country) {
        return new Coupon(id, code, createdAt, maxUsages, currentUsages, country);
    }

    /**
     * Registers a single usage of this coupon.
     *
     * @param requestCountry the country resolved from the caller's IP
     * @throws CouponExhaustedException      if the coupon has already reached its usage limit
     * @throws CouponCountryMismatchException if the caller's country does not match the coupon's country
     */
    public void use(Country requestCountry) {
        if (isExhausted()) {
            throw new CouponExhaustedException(code.value());
        }
        if (!this.country.equals(requestCountry)) {
            throw new CouponCountryMismatchException(this.country.code(), requestCountry.code());
        }
        this.currentUsages++;
    }

    public boolean isExhausted() {
        return currentUsages >= maxUsages;
    }

    public int getRemainingUsages() {
        return Math.max(0, maxUsages - currentUsages);
    }

    // --- accessors ---

    public UUID getId() {
        return id;
    }

    public CouponCode getCode() {
        return code;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getMaxUsages() {
        return maxUsages;
    }

    public int getCurrentUsages() {
        return currentUsages;
    }

    public Country getCountry() {
        return country;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Coupon other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Coupon{code=%s, usages=%d/%d, country=%s}".formatted(
                code.value(), currentUsages, maxUsages, country.code()
        );
    }
}
