package dev.patchreceipt.domain;

import java.util.List;
import java.util.Map;

public record VerificationReceipt(
        int schemaVersion,
        String receiptId,
        String engineVersion,
        String startedAt,
        String completedAt,
        long durationMs,
        String caseId,
        String caseTitle,
        String patchId,
        String patchTitle,
        Verdict verdict,
        String verdictSummary,
        String plainSummary,
        List<String> limitations,
        List<String> blockingReasons,
        List<String> warnings,
        Map<String, String> inputHashes,
        Map<String, String> toolchain,
        List<StageResult> stages,
        ReproductionEvidence reproduction,
        TestEvidence baselineRegression,
        TestEvidence patchedRegression,
        TestEvidence edgeCases,
        MutationEvidence mutation,
        ScopeEvidence scope,
        String receiptDigest) {

    public VerificationReceipt {
        plainSummary = plainSummary == null ? "" : plainSummary;
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
        blockingReasons = blockingReasons == null ? List.of() : List.copyOf(blockingReasons);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        inputHashes = inputHashes == null ? Map.of() : Map.copyOf(inputHashes);
        toolchain = toolchain == null ? Map.of() : Map.copyOf(toolchain);
        stages = stages == null ? List.of() : List.copyOf(stages);
        receiptDigest = receiptDigest == null ? "" : receiptDigest;
    }

    public VerificationReceipt withDigest(String digest) {
        return new VerificationReceipt(
                schemaVersion, receiptId, engineVersion, startedAt, completedAt, durationMs,
                caseId, caseTitle, patchId, patchTitle, verdict, verdictSummary,
                plainSummary, limitations, blockingReasons, warnings,
                inputHashes, toolchain, stages, reproduction,
                baselineRegression, patchedRegression, edgeCases, mutation, scope, digest);
    }
}
