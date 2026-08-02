package dev.patchreceipt.domain;

import java.util.List;

public record MutationEvidence(
        String provenance,
        boolean processHealthy,
        int totalMutants,
        int changedLineMutants,
        int killed,
        int survived,
        int uncovered,
        int timedOutOrErrored,
        double changedLineScore,
        double requiredScore,
        int requiredChangedLineMutants,
        boolean conclusive,
        List<String> filesWithoutMutants,
        List<MutationFinding> survivingMutants) {

    public MutationEvidence {
        filesWithoutMutants =
                filesWithoutMutants == null ? List.of() : List.copyOf(filesWithoutMutants);
        survivingMutants = survivingMutants == null ? List.of() : List.copyOf(survivingMutants);
    }
}
