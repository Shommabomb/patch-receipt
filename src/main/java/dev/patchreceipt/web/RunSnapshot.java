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
        List<StageProgress> stages,
        ReceiptLinks receipts) {

    public RunSnapshot {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public record StageProgress(
            String id,
            String title,
            StageStatus status,
            long durationMs,
            String summary) {
    }

    public record ReceiptLinks(String json, String markdown, String html) {
    }
}
