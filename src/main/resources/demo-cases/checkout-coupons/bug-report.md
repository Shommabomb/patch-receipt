# Duplicate coupons are applied more than once

Retried checkout requests can submit the same coupon more than once.

Coupon codes are case-insensitive, surrounding whitespace is not significant, and each canonical coupon code may affect an order only once. When duplicate entries disagree about the percentage, the first occurrence is authoritative. The supplied coupon list must not be modified.

The checkout total may never be negative.
