package dev.patchreceipt.verifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.patchreceipt.fixture.CheckoutCalculator;
import dev.patchreceipt.fixture.Coupon;
import java.util.List;
import org.junit.jupiter.api.Test;

class DuplicateCouponReproductionTest {

    @Test
    void retriedCouponAffectsOrderOnlyOnce() {
        var coupons = List.of(
                new Coupon("SAVE20", 20),
                new Coupon("SAVE20", 20));

        assertEquals(8_000, new CheckoutCalculator().totalAfterDiscount(10_000, coupons));
    }
}
