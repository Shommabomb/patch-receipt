package dev.patchreceipt.domain;

public record TestFailure(
        String testClass,
        String testName,
        String type,
        String message) {
}
