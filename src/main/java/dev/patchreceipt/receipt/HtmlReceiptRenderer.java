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
public final class HtmlReceiptRenderer {

    public String render(VerificationReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");

        StringBuilder output = new StringBuilder(16_384);
        output.append("""
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>PatchReceipt Evidence Receipt</title>
                  <style>
                    :root { color-scheme: light; --ink:#18212f; --muted:#5e6978; --line:#dbe2ea;
                      --panel:#f6f8fb; --pass:#147a45; --warn:#9a6300; --fail:#b42318; }
                    * { box-sizing:border-box; }
                    body { margin:0; background:#eef2f6; color:var(--ink);
                      font:15px/1.55 system-ui,-apple-system,"Segoe UI",sans-serif; }
                    main, header { width:min(1100px,calc(100%% - 32px)); margin:auto; }
                    header { padding:42px 0 18px; }
                    h1 { margin:0 0 12px; font-size:clamp(28px,5vw,44px); letter-spacing:-.03em; }
                    h2 { margin:0 0 16px; font-size:21px; }
                    h3 { margin:0; font-size:17px; }
                    section { margin:0 0 18px; padding:22px; background:white; border:1px solid var(--line);
                      border-radius:14px; box-shadow:0 3px 14px rgba(24,33,47,.045); }
                    .verdict { display:inline-block; margin:0 0 12px; padding:6px 11px; border-radius:999px;
                      color:white; background:var(--ink); font-weight:750; letter-spacing:.04em; }
                    .verified { background:var(--pass); } .partially-verified { background:var(--warn); }
                    .rejected { background:var(--fail); }
                    .summary { margin:0; font-size:18px; max-width:780px; }
                    .muted { color:var(--muted); }
                    .grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(220px,1fr)); gap:12px; }
                    .card { padding:14px; background:var(--panel); border-radius:10px; }
                    dl { margin:0; } dt { color:var(--muted); font-size:12px; font-weight:700;
                      text-transform:uppercase; letter-spacing:.05em; }
                    dd { margin:3px 0 12px; overflow-wrap:anywhere; }
                    table { width:100%%; border-collapse:collapse; }
                    th, td { padding:9px 10px; border-bottom:1px solid var(--line); text-align:left;
                      vertical-align:top; overflow-wrap:anywhere; }
                    th { color:var(--muted); font-size:12px; text-transform:uppercase; letter-spacing:.04em; }
                    .number { text-align:right; font-variant-numeric:tabular-nums; }
                    ul { margin:8px 0 0; padding-left:22px; }
                    .stage { padding:15px 0; border-top:1px solid var(--line); }
                    .stage:first-of-type { border-top:0; padding-top:0; }
                    .stage-head { display:flex; gap:10px; align-items:center; justify-content:space-between; }
                    .status { font-weight:750; }
                    pre, code { font-family:ui-monospace,SFMono-Regular,Consolas,monospace; }
                    pre { max-height:320px; overflow:auto; padding:13px; background:#111827; color:#e5e7eb;
                      border-radius:9px; white-space:pre-wrap; overflow-wrap:anywhere; }
                    code { overflow-wrap:anywhere; }
                    .digest { font-size:13px; word-break:break-all; }
                    @media print { body { background:white; } section { box-shadow:none; break-inside:avoid; } }
                  </style>
                </head>
                <body>
                <header>
                  <div class="verdict %s">%s</div>
                  <h1>PatchReceipt Evidence Receipt</h1>
                  <p class="summary">%s</p>
                </header>
                <main>
                """.formatted(verdictClass(receipt), html(value(receipt.verdict())),
                html(receipt.verdictSummary())));

        appendSummary(output, receipt);
        appendMessages(output, "Blocking reasons", receipt.blockingReasons(), "No blocking reasons.");
        appendMessages(output, "Warnings", receipt.warnings(), "No warnings.");
        appendMap(output, "Input hashes", receipt.inputHashes());
        appendMap(output, "Toolchain", receipt.toolchain());
        appendStages(output, receipt.stages());
        appendReproduction(output, receipt.reproduction());
        appendTestEvidence(output, "Baseline regression", receipt.baselineRegression());
        appendTestEvidence(output, "Patched regression", receipt.patchedRegression());
        appendTestEvidence(output, "Independent edge cases", receipt.edgeCases());
        appendMutation(output, receipt.mutation());
        appendScope(output, receipt.scope());
        output.append("<section id=\"receipt-digest\"><h2>Receipt digest</h2><code class=\"digest\">")
                .append(html(receipt.receiptDigest()))
                .append("</code></section>\n");
        output.append("</main>\n</body>\n</html>\n");
        return output.toString();
    }

    private static void appendSummary(StringBuilder output, VerificationReceipt receipt) {
        output.append("<section id=\"summary\"><h2>Summary</h2><div class=\"grid\">");
        summaryCard(output, "Receipt ID", receipt.receiptId());
        summaryCard(output, "Schema version", receipt.schemaVersion());
        summaryCard(output, "Engine version", receipt.engineVersion());
        summaryCard(output, "Case", value(receipt.caseTitle()) + " (" + value(receipt.caseId()) + ")");
        summaryCard(output, "Patch", value(receipt.patchTitle()) + " (" + value(receipt.patchId()) + ")");
        summaryCard(output, "Started", receipt.startedAt());
        summaryCard(output, "Completed", receipt.completedAt());
        summaryCard(output, "Duration", receipt.durationMs() + " ms");
        output.append("</div></section>\n");
    }

    private static void summaryCard(StringBuilder output, String label, Object value) {
        output.append("<div class=\"card\"><dl><dt>")
                .append(html(label))
                .append("</dt><dd>")
                .append(html(value(value)))
                .append("</dd></dl></div>");
    }

    private static void appendMessages(
            StringBuilder output, String title, List<String> messages, String emptyMessage) {
        output.append("<section><h2>").append(html(title)).append("</h2>");
        if (messages == null || messages.isEmpty()) {
            output.append("<p class=\"muted\">").append(html(emptyMessage)).append("</p>");
        } else {
            output.append("<ul>");
            messages.forEach(message -> output.append("<li>").append(html(message)).append("</li>"));
            output.append("</ul>");
        }
        output.append("</section>\n");
    }

    private static void appendMap(StringBuilder output, String title, Map<String, ?> values) {
        output.append("<section><h2>").append(html(title)).append("</h2>");
        if (values == null || values.isEmpty()) {
            output.append("<p class=\"muted\">None recorded.</p></section>\n");
            return;
        }
        output.append("<table><thead><tr><th>Name</th><th>Value</th></tr></thead><tbody>");
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsFirst(String::compareTo)))
                .forEach(entry -> output.append("<tr><td>")
                        .append(html(entry.getKey()))
                        .append("</td><td><code>")
                        .append(html(value(entry.getValue())))
                        .append("</code></td></tr>"));
        output.append("</tbody></table></section>\n");
    }

    private static void appendStages(StringBuilder output, List<StageResult> stages) {
        output.append("<section id=\"stages\"><h2>Verification stages</h2>");
        if (stages == null || stages.isEmpty()) {
            output.append("<p class=\"muted\">No stages recorded.</p></section>\n");
            return;
        }
        for (StageResult stage : stages) {
            if (stage == null) {
                continue;
            }
            output.append("<article class=\"stage\"><div class=\"stage-head\"><h3>")
                    .append(html(stage.title()))
                    .append("</h3><span class=\"status\">")
                    .append(html(value(stage.status())))
                    .append("</span></div><p>")
                    .append(html(stage.summary()))
                    .append("</p><dl><dt>Stage ID</dt><dd><code>")
                    .append(html(stage.id()))
                    .append("</code></dd><dt>Duration</dt><dd>")
                    .append(stage.durationMs())
                    .append(" ms</dd></dl>");
            if (stage.metrics() != null && !stage.metrics().isEmpty()) {
                output.append("<table><thead><tr><th>Metric</th><th>Value</th></tr></thead><tbody>");
                stage.metrics().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.nullsFirst(String::compareTo)))
                        .forEach(entry -> output.append("<tr><td>")
                                .append(html(entry.getKey()))
                                .append("</td><td>")
                                .append(html(value(entry.getValue())))
                                .append("</td></tr>"));
                output.append("</tbody></table>");
            }
            if (stage.log() != null && !stage.log().isBlank()) {
                output.append("<details><summary>Sanitized log</summary><pre>")
                        .append(html(stage.log()))
                        .append("</pre></details>");
            }
            output.append("</article>");
        }
        output.append("</section>\n");
    }

    private static void appendReproduction(StringBuilder output, ReproductionEvidence reproduction) {
        output.append("<section id=\"reproduction\"><h2>Bug reproduction</h2>");
        if (reproduction == null) {
            output.append("<p class=\"muted\">No reproduction evidence recorded.</p></section>\n");
            return;
        }
        output.append("<div class=\"grid\">");
        summaryCard(output, "Test class", reproduction.testClass());
        summaryCard(output, "Expected failure type", reproduction.expectedFailureType());
        summaryCard(output, "Expected baseline failure observed",
                reproduction.expectedBaselineFailureObserved() ? "Yes" : "No");
        output.append("</div>");
        appendTestEvidenceBody(output, "Reproduction before patch", reproduction.baseline());
        appendTestEvidenceBody(output, "Reproduction after patch", reproduction.patched());
        output.append("</section>\n");
    }

    private static void appendTestEvidence(
            StringBuilder output, String title, TestEvidence evidence) {
        output.append("<section><h2>").append(html(title)).append("</h2>");
        appendTestEvidenceContents(output, evidence);
        output.append("</section>\n");
    }

    private static void appendTestEvidenceBody(
            StringBuilder output, String title, TestEvidence evidence) {
        output.append("<h3 style=\"margin-top:20px\">").append(html(title)).append("</h3>");
        appendTestEvidenceContents(output, evidence);
    }

    private static void appendTestEvidenceContents(StringBuilder output, TestEvidence evidence) {
        if (evidence == null) {
            output.append("<p class=\"muted\">No test evidence recorded.</p>");
            return;
        }
        output.append("""
                <table><thead><tr><th class="number">Tests</th><th class="number">Passed</th>
                <th class="number">Failures</th><th class="number">Errors</th>
                <th class="number">Skipped</th><th class="number">Duration</th></tr></thead><tbody><tr>
                """);
        numberCell(output, evidence.tests());
        numberCell(output, evidence.passed());
        numberCell(output, evidence.failures());
        numberCell(output, evidence.errors());
        numberCell(output, evidence.skipped());
        numberCell(output, evidence.durationMs() + " ms");
        output.append("</tr></tbody></table>");
        if (!evidence.failureDetails().isEmpty()) {
            output.append("<h3>Failures</h3><ul>");
            for (TestFailure failure : evidence.failureDetails()) {
                output.append("<li><code>")
                        .append(html(failure.testClass()))
                        .append("#")
                        .append(html(failure.testName()))
                        .append("</code> — ")
                        .append(html(failure.type()))
                        .append(": ")
                        .append(html(failure.message()))
                        .append("</li>");
            }
            output.append("</ul>");
        }
    }

    private static void numberCell(StringBuilder output, Object value) {
        output.append("<td class=\"number\">").append(html(value(value))).append("</td>");
    }

    private static void appendMutation(StringBuilder output, MutationEvidence mutation) {
        output.append("<section id=\"mutation\"><h2>Mutation evidence</h2>");
        if (mutation == null) {
            output.append("<p class=\"muted\">No mutation evidence recorded.</p></section>\n");
            return;
        }
        output.append("<div class=\"grid\">");
        summaryCard(output, "Provenance", mutation.provenance());
        summaryCard(output, "Total mutants", mutation.totalMutants());
        summaryCard(output, "Changed-line mutants", mutation.changedLineMutants());
        summaryCard(output, "Killed", mutation.killed());
        summaryCard(output, "Survived", mutation.survived());
        summaryCard(output, "Uncovered", mutation.uncovered());
        summaryCard(output, "Timed out or errored", mutation.timedOutOrErrored());
        summaryCard(output, "Changed-line score", percentage(mutation.changedLineScore()));
        summaryCard(output, "Required score", percentage(mutation.requiredScore()));
        summaryCard(output, "Conclusive", mutation.conclusive() ? "Yes" : "No");
        output.append("</div>");
        if (!mutation.survivingMutants().isEmpty()) {
            output.append("<h3 style=\"margin-top:20px\">Surviving mutants</h3><ul>");
            for (MutationFinding finding : mutation.survivingMutants()) {
                output.append("<li><code>")
                        .append(html(finding.mutatedClass()))
                        .append("#")
                        .append(html(finding.mutatedMethod()))
                        .append(":")
                        .append(finding.lineNumber())
                        .append("</code> — ")
                        .append(html(finding.status()))
                        .append("; ")
                        .append(html(finding.mutator()))
                        .append("; ")
                        .append(html(finding.description()));
                if (finding.killingTest() != null && !finding.killingTest().isBlank()) {
                    output.append("; killing test: ").append(html(finding.killingTest()));
                }
                output.append("</li>");
            }
            output.append("</ul>");
        }
        output.append("</section>\n");
    }

    private static void appendScope(StringBuilder output, ScopeEvidence scope) {
        output.append("<section id=\"scope\"><h2>Scope analysis</h2>");
        if (scope == null) {
            output.append("<p class=\"muted\">No scope evidence recorded.</p></section>\n");
            return;
        }
        output.append("<div class=\"grid\">");
        summaryCard(output, "Files changed", scope.filesChanged());
        summaryCard(output, "Additions", scope.additions());
        summaryCard(output, "Deletions", scope.deletions());
        output.append("</div>");
        if (!scope.files().isEmpty()) {
            output.append("""
                    <table><thead><tr><th>Path</th><th class="number">Additions</th>
                    <th class="number">Deletions</th><th>Expected</th><th>Forbidden</th>
                    <th>Changed lines</th></tr></thead><tbody>
                    """);
            scope.files().stream()
                    .sorted(Comparator.comparing(ChangedFile::path, Comparator.nullsFirst(String::compareTo)))
                    .forEach(file -> output.append("<tr><td><code>")
                            .append(html(file.path()))
                            .append("</code></td><td class=\"number\">")
                            .append(file.additions())
                            .append("</td><td class=\"number\">")
                            .append(file.deletions())
                            .append("</td><td>")
                            .append(file.expected() ? "Yes" : "No")
                            .append("</td><td>")
                            .append(file.forbidden() ? "Yes" : "No")
                            .append("</td><td>")
                            .append(html(file.changedLines().stream()
                                    .sorted()
                                    .map(String::valueOf)
                                    .collect(Collectors.joining(", "))))
                            .append("</td></tr>"));
            output.append("</tbody></table>");
        }
        appendNestedMessages(output, "Hard violations", scope.hardViolations());
        appendNestedMessages(output, "Scope warnings", scope.warnings());
        output.append("</section>\n");
    }

    private static void appendNestedMessages(StringBuilder output, String label, List<String> values) {
        output.append("<h3 style=\"margin-top:20px\">").append(html(label)).append("</h3>");
        if (values == null || values.isEmpty()) {
            output.append("<p class=\"muted\">None.</p>");
            return;
        }
        output.append("<ul>");
        values.forEach(value -> output.append("<li>").append(html(value)).append("</li>"));
        output.append("</ul>");
    }

    private static String verdictClass(VerificationReceipt receipt) {
        if (receipt.verdict() == null) {
            return "";
        }
        return receipt.verdict().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String percentage(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private static String value(Object value) {
        return value == null || value.toString().isBlank() ? "Not recorded" : value.toString();
    }

    private static String html(Object value) {
        String text = value(value);
        StringBuilder escaped = new StringBuilder(text.length() + 16);
        for (int index = 0; index < text.length(); index++) {
            switch (text.charAt(index)) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(text.charAt(index));
            }
        }
        return escaped.toString();
    }
}
