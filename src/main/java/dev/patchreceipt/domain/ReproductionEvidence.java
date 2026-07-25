package dev.patchreceipt.domain;

public record ReproductionEvidence(
        String testClass,
        String expectedFailureType,
        boolean expectedBaselineFailureObserved,
        TestEvidence baseline,
        TestEvidence patched) {
}
