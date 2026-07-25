package dev.patchreceipt.fixture;

import java.util.List;

public final class CheckoutCalculator {

    public int totalAfterDiscount(int subtotalCents, List<Coupon> coupons) {
        if (subtotalCents < 0) {
            throw new IllegalArgumentException("Subtotal must not be negative");
        }

        int total = subtotalCents;
        for (Coupon coupon : coupons) {
            total -= subtotalCents * coupon.percentage() / 100;
        }
        return Math.max(0, total);
    }
}
