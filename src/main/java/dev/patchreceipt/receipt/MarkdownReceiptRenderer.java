package dev.patchreceipt.receipt;

import dev.patchreceipt.domain.ChangedFile;
import dev.patchreceipt.domain.MutationEvidence;
import dev.patchreceipt.domain.MutationFinding;
import dev.patchreceipt.domain.ReproductionEvidence;
import dev.patchreceipt.domain.ScopeEvidence;
import dev.patchreceipt.domain.StageResult;
import dev.patchreceipt.domain.TestEvidence;
import dev.patchreceipt.domain.TestFailure;
import dev.patchreceipt.domain.VerificationReceipt;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public final class MarkdownReceiptRenderer {

    public String render(VerificationReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");

        StringBuilder output = new StringBuilder(8_192);
        output.append("# PatchReceipt Evidence Receipt\n\n");
        output.append("**Verdict:** `").append(value(receipt.verdict())).append("`\n\n");
        output.append(escape(receipt.verdictSummary())).append("\n\n");

        appendMetadata(output, receipt);
        appendStringList(output, "Blocking reasons", receipt.blockingReasons(), "None.");
        appendStringList(output, "Warnings", receipt.warnings(), "None.");
        appendMap(output, "Input hashes", receipt.inputHashes());
        appendMap(output, "Toolchain", receipt.toolchain());
        appendStages(output, receipt.stages());
        appendReproduction(output, receipt.reproduction());
        appendTestEvidence(output, "Baseline regression", receipt.baselineRegression());
        appendTestEvidence(output, "Patched regression", receipt.patchedRegression());
        appendTestEvidence(output, "Independent edge cases", receipt.edgeCases());
        appendMutation(output, receipt.mutation());
        appendScope(output, receipt.scope());

        output.append("## Receipt digest\n\n");
        output.append("`").append(escapeInlineCode(receipt.receiptDigest())).append("`\n");
        return output.toString();
    }

    private static void appendMetadata(StringBuilder output, VerificationReceipt receipt) {
        output.append("## Summary\n\n");
        output.append("| Field | Value |\n");
        output.append("| --- | --- |\n");
        row(output, "Receipt ID", receipt.receiptId());
        row(output, "Schema version", receipt.schemaVersion());
        row(output, "Engine version", receipt.engineVersion());
        row(output, "Case", value(receipt.caseTitle()) + " (" + value(receipt.caseId()) + ")");
        row(output, "Patch", value(receipt.patchTitle()) + " (" + value(receipt.patchId()) + ")");
        row(output, "Started", receipt.startedAt());
        row(output, "Completed", receipt.completedAt());
        row(output, "Duration", receipt.durationMs() + " ms");
        output.append("\n");
    }

    private static void appendStringList(
            StringBuilder output, String title, List<String> values, String emptyMessage) {
        output.append("## ").append(title).append("\n\n");
        if (values == null || values.isEmpty()) {
            output.append(emptyMessage).append("\n\n");
            return;
        }
        values.forEach(item -> output.append("- ").append(escape(item)).append("\n"));
        output.append("\n");
    }

    private static void appendMap(StringBuilder output, String title, Map<String, ?> values) {
        output.append("## ").append(title).append("\n\n");
        if (values == null || values.isEmpty()) {
            output.append("None recorded.\n\n");
            return;
        }
        output.append("| Name | Value |\n");
        output.append("| --- | --- |\n");
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsFirst(String::compareTo)))
                .forEach(entry -> {
                    row(output, entry.getKey(), entry.getValue());
                });
        output.append("\n");
    }

    private static void appendStages(StringBuilder output, List<StageResult> stages) {
        output.append("## Verification stages\n\n");
        if (stages == null || stages.isEmpty()) {
            output.append("No stages recorded.\n\n");
            return;
        }
        for (StageResult stage : stages) {
            if (stage == null) {
                continue;
            }
            output.append("### ")
                    .append(escape(stage.title()))
                    .append(" — `")
                    .append(value(stage.status()))
                    .append("`\n\n");
            output.append("- ID: `").append(escapeInlineCode(stage.id())).append("`\n");
            output.append("- Duration: ").append(stage.durationMs()).append(" ms\n");
            output.append("- Summary: ").append(escape(stage.summary())).append("\n");
            if (stage.metrics() != null && !stage.metrics().isEmpty()) {
                output.append("- Metrics:\n");
                stage.metrics().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.nullsFirst(String::compareTo)))
                        .forEach(entry -> output.append("  - ")
                                .append(escape(entry.getKey()))
                                .append(": ")
                                .append(escape(value(entry.getValue())))
                                .append("\n"));
            }
            if (stage.log() != null && !stage.log().isBlank()) {
                output.append("- Sanitized log:\n\n");
                appendIndentedCode(output, stage.log());
            }
            output.append("\n");
        }
    }

    private static void appendReproduction(StringBuilder output, ReproductionEvidence reproduction) {
        output.append("## Bug reproduction\n\n");
        if (reproduction == null) {
            output.append("No reproduction evidence recorded.\n\n");
            return;
        }
        output.append("- Test class: `")
                .append(escapeInlineCode(reproduction.testClass()))
                .append("`\n");
        output.append("- Expected failure type: `")
                .append(escapeInlineCode(reproduction.expectedFailureType()))
                .append("`\n");
        output.append("- Expected baseline failure observed: **")
                .append(reproduction.expectedBaselineFailureObserved() ? "yes" : "no")
                .append("**\n\n");
        appendTestEvidence(output, "Reproduction before patch", reproduction.baseline());
        appendTestEvidence(output, "Reproduction after patch", reproduction.patched());
    }

    private static void appendTestEvidence(
            StringBuilder output, String title, TestEvidence evidence) {
        output.append("## ").append(title).append("\n\n");
        if (evidence == null) {
            output.append("No test evidence recorded.\n\n");
            return;
        }
        output.append("| Tests | Passed | Failures | Errors | Skipped | Duration |\n");
        output.append("| ---: | ---: | ---: | ---: | ---: | ---: |\n");
        output.append("| ")
                .append(evidence.tests()).append(" | ")
                .append(evidence.passed()).append(" | ")
                .append(evidence.failures()).append(" | ")
                .append(evidence.errors()).append(" | ")
                .append(evidence.skipped()).append(" | ")
                .append(evidence.durationMs()).append(" ms |\n\n");
        if (!evidence.failureDetails().isEmpty()) {
            output.append("Failures:\n\n");
            for (TestFailure failure : evidence.failureDetails()) {
                output.append("- `")
                        .append(escapeInlineCode(failure.testClass()))
                        .append("#")
                        .append(escapeInlineCode(failure.testName()))
                        .append("` — ")
                        .append(escape(failure.type()))
                        .append(": ")
                        .append(escape(failure.message()))
                        .append("\n");
            }
            output.append("\n");
        }
    }

    private static void appendMutation(StringBuilder output, MutationEvidence mutation) {
        output.append("## Mutation evidence\n\n");
        if (mutation == null) {
            output.append("No mutation evidence recorded.\n\n");
            return;
        }
        output.append("| Field | Value |\n");
        output.append("| --- | ---: |\n");
        row(output, "Provenance", mutation.provenance());
        row(output, "Total mutants", mutation.totalMutants());
        row(output, "Changed-line mutants", mutation.changedLineMutants());
        row(output, "Killed", mutation.killed());
        row(output, "Survived", mutation.survived());
        row(output, "Uncovered", mutation.uncovered());
        row(output, "Timed out or errored", mutation.timedOutOrErrored());
        row(output, "Changed-line score", percentage(mutation.changedLineScore()));
        row(output, "Required score", percentage(mutation.requiredScore()));
        row(output, "Conclusive", mutation.conclusive() ? "yes" : "no");
        output.append("\n");

        if (!mutation.survivingMutants().isEmpty()) {
            output.append("### Surviving mutants\n\n");
            for (MutationFinding finding : mutation.survivingMutants()) {
                output.append("- `")
                        .append(escapeInlineCode(finding.mutatedClass()))
                        .append("#")
                        .append(escapeInlineCode(finding.mutatedMethod()))
                        .append(":")
                        .append(finding.lineNumber())
                        .append("` — ")
                        .append(escape(finding.status()))
                        .append("; ")
                        .append(escape(finding.mutator()))
                        .append("; ")
                        .append(escape(finding.description()));
                if (finding.killingTest() != null && !finding.killingTest().isBlank()) {
                    output.append("; killing test: ")
                            .append(escape(finding.killingTest()));
                }
                output.append("\n");
            }
            output.append("\n");
        }
    }

    private static void appendScope(StringBuilder output, ScopeEvidence scope) {
        output.append("## Scope analysis\n\n");
        if (scope == null) {
            output.append("No scope evidence recorded.\n\n");
            return;
        }
        output.append("- Files changed: ").append(scope.filesChanged()).append("\n");
        output.append("- Additions: ").append(scope.additions()).append("\n");
        output.append("- Deletions: ").append(scope.deletions()).append("\n\n");

        if (!scope.files().isEmpty()) {
            output.append("| Path | Additions | Deletions | Expected | Forbidden | Changed lines |\n");
            output.append("| --- | ---: | ---: | --- | --- | --- |\n");
            scope.files().stream()
                    .sorted(Comparator.comparing(ChangedFile::path, Comparator.nullsFirst(String::compareTo)))
                    .forEach(file -> row(output,
                            file.path(),
                            file.additions(),
                            file.deletions(),
                            file.expected() ? "yes" : "no",
                            file.forbidden() ? "yes" : "no",
                            file.changedLines().stream()
                                    .sorted()
                                    .map(String::valueOf)
                                    .collect(Collectors.joining(", "))));
            output.append("\n");
        }
        appendNestedList(output, "Hard violations", scope.hardViolations());
        appendNestedList(output, "Scope warnings", scope.warnings());
        output.append("\n");
    }

    private static void appendNestedList(StringBuilder output, String label, List<String> values) {
        output.append("**").append(label).append(":**\n\n");
        if (values == null || values.isEmpty()) {
            output.append("- None.\n\n");
            return;
        }
        values.forEach(value -> output.append("- ").append(escape(value)).append("\n"));
        output.append("\n");
    }

    private static void appendIndentedCode(StringBuilder output, String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n", -1)) {
            output.append("    ").append(line).append("\n");
        }
    }

    private static void row(StringBuilder output, Object... cells) {
        output.append("|");
        for (Object cell : cells) {
            output.append(" ").append(escapeTable(value(cell))).append(" |");
        }
        output.append("\n");
    }

    private static String percentage(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private static String value(Object value) {
        return value == null ? "Not recorded" : String.valueOf(value);
    }

    private static String escape(String value) {
        if (value == null || value.isBlank()) {
            return "Not recorded";
        }
        return value
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapeTable(String value) {
        return escape(value)
                .replace("|", "\\|")
                .replace("\r\n", "<br>")
                .replace("\n", "<br>")
                .replace("\r", "<br>");
    }

    private static String escapeInlineCode(String value) {
        return value(value).replace("`", "&#96;");
    }
}
