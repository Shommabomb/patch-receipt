package dev.patchreceipt.domain;

import java.util.List;

public record MutationEvidence(
        String provenance,
        int totalMutants,
        int changedLineMutants,
        int killed,
        int survived,
        int uncovered,
        int timedOutOrErrored,
        double changedLineScore,
        double requiredScore,
        boolean conclusive,
        List<MutationFinding> survivingMutants) {

    public MutationEvidence {
        survivingMutants = survivingMutants == null ? List.of() : List.copyOf(survivingMutants);
    }
}
