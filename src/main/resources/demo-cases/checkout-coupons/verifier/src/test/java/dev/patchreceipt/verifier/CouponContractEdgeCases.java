package dev.patchreceipt.verifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.patchreceipt.fixture.CheckoutCalculator;
import dev.patchreceipt.fixture.Coupon;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class CouponContractEdgeCases {

    private final CheckoutCalculator calculator = new CheckoutCalculator();

    @TestFactory
    Stream<DynamicTest> generatedContractCases() {
        return Stream.of(
                check("two identical retries", 8_000, 10_000,
                        new Coupon("SAVE20", 20), new Coupon("SAVE20", 20)),
                check("three identical retries", 8_000, 10_000,
                        new Coupon("SAVE20", 20), new Coupon("SAVE20", 20),
                        new Coupon("SAVE20", 20)),
                check("case variants are one code", 8_000, 10_000,
                        new Coupon("SAVE20", 20), new Coupon("save20", 20)),
                check("surrounding whitespace is ignored", 8_000, 10_000,
                        new Coupon("SAVE20", 20), new Coupon("  save20  ", 20)),
                check("first conflicting percentage wins", 8_000, 10_000,
                        new Coupon("SAVE20", 20), new Coupon("save20", 75)),
                check("different canonical codes both apply", 7_000, 10_000,
                        new Coupon("SAVE20", 20), new Coupon("LOYAL10", 10)),
                check("near-zero subtotal reaches zero", 0, 1,
                        new Coupon("FREE", 100)),
                check("combined discounts never go negative", 0, 100,
                        new Coupon("A", 80), new Coupon("B", 80)),
                DynamicTest.dynamicTest("input list is not modified", () -> {
                    var coupons = new ArrayList<>(List.of(
                            new Coupon("SAVE20", 20),
                            new Coupon("save20", 20)));
                    var original = List.copyOf(coupons);
                    calculator.totalAfterDiscount(10_000, coupons);
                    assertEquals(original, coupons, "input list is not modified");
                }));
    }

    private DynamicTest check(
            String name,
            int expected,
            int subtotal,
            Coupon... coupons) {
        return DynamicTest.dynamicTest(name, () ->
                assertEquals(
                        expected,
                        calculator.totalAfterDiscount(subtotal, List.of(coupons)),
                        name));
    }
}
