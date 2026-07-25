package dev.patchreceipt.domain;

import java.util.Map;

public record StageResult(
        String id,
        String title,
        StageStatus status,
        long durationMs,
        String summary,
        Map<String, Object> metrics,
        String log) {

    public StageResult {
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        log = log == null ? "" : log;
    }
}
