package dev.patchreceipt.fixture;

import java.util.Objects;

public record Coupon(String code, int percentage) {

    public Coupon {
        Objects.requireNonNull(code, "code");
        if (code.isBlank()) {
            throw new IllegalArgumentException("Coupon code must not be blank");
        }
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("Coupon percentage must be between 0 and 100");
        }
    }
}
