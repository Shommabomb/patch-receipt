package dev.patchreceipt.domain;

public record MutationFinding(
        String status,
        String mutatedClass,
        String mutatedMethod,
        int lineNumber,
        String mutator,
        String description,
        String killingTest) {
}
