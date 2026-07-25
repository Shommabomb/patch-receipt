package dev.patchreceipt.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CheckoutCalculatorTest {

    private final CheckoutCalculator calculator = new CheckoutCalculator();

    @Test
    void leavesSubtotalUnchangedWithoutCoupons() {
        assertEquals(10_000, calculator.totalAfterDiscount(10_000, List.of()));
    }

    @Test
    void appliesOneCoupon() {
        assertEquals(8_000, calculator.totalAfterDiscount(
                10_000, List.of(new Coupon("SAVE20", 20))));
    }

    @Test
    void appliesDifferentCoupons() {
        assertEquals(7_000, calculator.totalAfterDiscount(
                10_000, List.of(new Coupon("SAVE20", 20), new Coupon("LOYAL10", 10))));
    }

    @Test
    void roundsEachIntegerDiscountDown() {
        assertEquals(900, calculator.totalAfterDiscount(
                999, List.of(new Coupon("TEN", 10))));
    }

    @Test
    void supportsAFullDiscount() {
        assertEquals(0, calculator.totalAfterDiscount(
                500, List.of(new Coupon("FREE", 100))));
    }

    @Test
    void rejectsNegativeSubtotals() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.totalAfterDiscount(-1, List.of()));
    }
}
