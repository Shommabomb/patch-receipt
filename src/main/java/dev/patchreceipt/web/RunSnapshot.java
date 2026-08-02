package dev.patchreceipt.web;

import dev.patchreceipt.domain.StageStatus;
import dev.patchreceipt.domain.Verdict;
import java.util.List;

public record RunSnapshot(
        String runId,
        String caseId,
        String patchId,
        RunState state,
        String currentStage,
        String createdAt,
        String completedAt,
        Verdict verdict,
        String summary,
        String plainSummary,
        String failureMessage,
        List<String> limitations,
        List<StageProgress> stages,
        EvidenceSummary evidence,
        ReceiptLinks receipts) {

    public RunSnapshot {
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public record StageProgress(
            String id,
            String title,
            StageStatus status,
            long durationMs,
            String summary) {
    }

    public record EvidenceSummary(
            List<Metric> metrics,
            List<String> findings) {

        public EvidenceSummary {
            metrics = metrics == null ? List.of() : List.copyOf(metrics);
            findings = findings == null ? List.of() : List.copyOf(findings);
        }
    }

    public record Metric(String label, String value, String description) {
    }

    public record ReceiptLinks(String json, String markdown, String html) {
    }
}
