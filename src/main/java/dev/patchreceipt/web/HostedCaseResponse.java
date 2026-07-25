package dev.patchreceipt.web;

import java.util.List;

public record HostedCaseResponse(
        String caseId,
        String title,
        String summary,
        String bugReport,
        List<HostedPatchResponse> patches) {

    public record HostedPatchResponse(
            String patchId,
            String title,
            String description,
            String unifiedDiff) {
    }
}
