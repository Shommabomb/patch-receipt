package dev.patchreceipt.receipt;

import dev.patchreceipt.domain.ChangedFile;
import dev.patchreceipt.domain.MutationEvidence;
import dev.patchreceipt.domain.MutationFinding;
import dev.patchreceipt.domain.ReproductionEvidence;
import dev.patchreceipt.domain.ScopeEvidence;
import dev.patchreceipt.domain.StageResult;
import dev.patchreceipt.domain.StageStatus;
import dev.patchreceipt.domain.TestEvidence;
import dev.patchreceipt.domain.TestFailure;
import dev.patchreceipt.domain.VerificationReceipt;
import dev.patchreceipt.domain.Verdict;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptRendererTests {

    private final MarkdownReceiptRenderer markdownRenderer = new MarkdownReceiptRenderer();
    private final HtmlReceiptRenderer htmlRenderer = new HtmlReceiptRenderer();
    private final JsonReceiptRenderer jsonRenderer = new JsonReceiptRenderer();

    @Test
    void rendersCanonicalEvidenceWithParityAcrossJsonMarkdownAndHtml() {
        VerificationReceipt receipt = receipt();

        String markdown = markdownRenderer.render(receipt);
        String html = htmlRenderer.render(receipt);
        String json = jsonRenderer.render(receipt);

        assertThat(MarkdownReceiptRenderer.class).hasAnnotation(Component.class);
        assertThat(HtmlReceiptRenderer.class).hasAnnotation(Component.class);
        assertThat(JsonReceiptRenderer.class).hasAnnotation(Component.class);

        for (String evidence : List.of(
                "VERIFIED",
                "receipt-123",
                "checkout-coupons",
                "minimal-robust",
                "All correctness gates passed",
                "sha256-project",
                "Baseline regression",
                "baseline_regression",
                "DuplicateCouponReproductionTest",
                "CheckoutCalculator.java",
                "Every canonical check passed.",
                "Design and performance were not reviewed.",
                "OBSERVED_FILESYSTEM",
                "digest-abc123")) {
            assertThat(markdown).contains(evidence);
            assertThat(html).contains(evidence);
            assertThat(json).contains(evidence);
        }

        assertThat(json)
                .contains("\"schemaVersion\" : 2")
                .contains("\"plainSummary\" : \"Every canonical check passed.\"")
                .contains("\"limitations\" : [")
                .contains("\"processHealthy\" : true")
                .contains(
                        "\"baselineRegression\"",
                        "\"patchedRegression\"",
                        "\"edgeCases\"",
                        "\"changedLines\"");
        assertThat(markdown).contains(
                "Reproduction before patch",
                "Reproduction after patch",
                "Patched regression",
                "Independent edge cases",
                "Process healthy",
                "Changed lines");
        assertThat(html).contains(
                "Reproduction before patch",
                "Reproduction after patch",
                "Patched regression",
                "Independent edge cases",
                "Process healthy",
                "Changed lines");
        assertThat(markdown).contains("| 9 | 9 | 0 | 0 | 0 | 94 ms |");
        assertThat(html).contains("<td class=\"number\">9</td>");
        assertThat(markdown.indexOf("alpha")).isLessThan(markdown.indexOf("zeta"));
        assertThat(html.indexOf("alpha")).isLessThan(html.indexOf("zeta"));
    }

    @Test
    void htmlIsStandaloneUtf8DeterministicAndEscapesEveryUntrustedSurface() {
        VerificationReceipt receipt = receipt();

        String first = htmlRenderer.render(receipt);
        String second = htmlRenderer.render(receipt);

        assertThat(first)
                .isEqualTo(second)
                .startsWith("<!doctype html>")
                .contains("<meta charset=\"UTF-8\">")
                .contains("Content-Security-Policy")
                .contains("default-src 'none'")
                .contains("<html lang=\"en\">")
                .endsWith("</html>\n")
                .contains("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;")
                .contains("failure &lt;unsafe&gt; &amp; detail")
                .contains("log &lt;b&gt;unsafe&lt;/b&gt; &amp; trace")
                .contains("src/main/java/&lt;unsafe&gt;.java")
                .doesNotContain("<script>alert(\"x\")</script>")
                .doesNotContain("<b>unsafe</b>");
    }

    @Test
    void markdownIsDeterministicAndRetainsFailureMutationAndScopeDetails() {
        String first = markdownRenderer.render(receipt());
        String second = markdownRenderer.render(receipt());

        assertThat(first)
                .isEqualTo(second)
                .startsWith("# PatchReceipt Evidence Receipt")
                .contains("failure &lt;unsafe&gt; & detail")
                .contains("SURVIVED")
                .contains("removed conditional")
                .contains("Scope warnings")
                .contains("unexpected production path")
                .contains("Changed lines")
                .contains("Mutation testing introduces small code changes")
                .endsWith("`digest-abc123`\n");
    }

    @Test
    void digestCoversCanonicalSummaryAndLimitations() {
        ReceiptDigestService digests = new ReceiptDigestService();
        VerificationReceipt original = receipt();
        VerificationReceipt first = digests.attachDigest(withLanguage(
                original,
                "Every canonical check passed.",
                List.of("Design and performance were not reviewed.")));
        VerificationReceipt changedSummary = digests.attachDigest(withLanguage(
                original,
                "Different summary.",
                List.of("Design and performance were not reviewed.")));
        VerificationReceipt changedLimitations = digests.attachDigest(withLanguage(
                original,
                "Every canonical check passed.",
                List.of("Different limitation.")));

        assertThat(first.receiptDigest()).hasSize(64);
        assertThat(changedSummary.receiptDigest()).isNotEqualTo(first.receiptDigest());
        assertThat(changedLimitations.receiptDigest()).isNotEqualTo(first.receiptDigest());
        assertThat(jsonRenderer.render(first))
                .contains("\"receiptDigest\" : \"" + first.receiptDigest() + "\"");
    }

    private static VerificationReceipt receipt() {
        TestFailure failure = new TestFailure(
                "DuplicateCouponReproductionTest",
                "duplicateCouponIsAppliedOnce",
                "AssertionError",
                "failure <unsafe> & detail");
        TestEvidence baselineReproduction = new TestEvidence(1, 0, 1, 0, 0, 31, List.of(failure));
        TestEvidence passingReproduction = new TestEvidence(1, 1, 0, 0, 0, 27, List.of());
        TestEvidence baselineRegression = new TestEvidence(6, 6, 0, 0, 0, 82, List.of());
        TestEvidence patchedRegression = new TestEvidence(6, 6, 0, 0, 0, 79, List.of());
        TestEvidence edgeCases = new TestEvidence(9, 9, 0, 0, 0, 94, List.of());

        ReproductionEvidence reproduction = new ReproductionEvidence(
                "DuplicateCouponReproductionTest",
                "org.opentest4j.AssertionFailedError",
                true,
                baselineReproduction,
                passingReproduction);

        MutationFinding finding = new MutationFinding(
                "SURVIVED",
                "dev.patchreceipt.fixture.CheckoutCalculator",
                "calculate",
                42,
                "ConditionalsBoundaryMutator",
                "removed conditional",
                "");
        MutationEvidence mutation = new MutationEvidence(
                "LIVE_PIT",
                true,
                12,
                5,
                4,
                1,
                0,
                0,
                80.0,
                80.0,
                2,
                true,
                List.of(),
                List.of(finding));

        ScopeEvidence scope = new ScopeEvidence(
                "OBSERVED_FILESYSTEM",
                2,
                7,
                2,
                List.of(
                        new ChangedFile(
                                "src/main/java/dev/patchreceipt/fixture/CheckoutCalculator.java",
                                6,
                                2,
                                true,
                                false,
                                Set.of(42, 41)),
                        new ChangedFile(
                                "src/main/java/<unsafe>.java",
                                1,
                                0,
                                false,
                                false,
                                Set.of(3))),
                List.of(),
                List.of("unexpected production path"));

        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put("zeta", "sha256-verifier");
        hashes.put("alpha", "sha256-project");
        Map<String, String> toolchain = new LinkedHashMap<>();
        toolchain.put("maven", "3.9.16");
        toolchain.put("java", "21");
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("zeta", 6);
        metrics.put("alpha", "all passed");

        StageResult stage = new StageResult(
                "baseline_regression",
                "Baseline regression",
                StageStatus.PASS,
                82,
                "Original tests passed <script>alert(\"x\")</script>",
                metrics,
                "log <b>unsafe</b> & trace");

        return new VerificationReceipt(
                2,
                "receipt-123",
                "0.1.0",
                "2026-07-25T12:00:00Z",
                "2026-07-25T12:00:01Z",
                1_000,
                "checkout-coupons",
                "Checkout coupon retries",
                "minimal-robust",
                "Minimal robust patch",
                Verdict.VERIFIED,
                "All correctness gates passed <script>alert(\"x\")</script>",
                "Every canonical check passed.",
                List.of("Design and performance were not reviewed."),
                List.of(),
                List.of("review warning"),
                hashes,
                toolchain,
                List.of(stage),
                reproduction,
                baselineRegression,
                patchedRegression,
                edgeCases,
                mutation,
                scope,
                "digest-abc123");
    }

    private static VerificationReceipt withLanguage(
            VerificationReceipt receipt,
            String plainSummary,
            List<String> limitations) {
        return new VerificationReceipt(
                receipt.schemaVersion(),
                receipt.receiptId(),
                receipt.engineVersion(),
                receipt.startedAt(),
                receipt.completedAt(),
                receipt.durationMs(),
                receipt.caseId(),
                receipt.caseTitle(),
                receipt.patchId(),
                receipt.patchTitle(),
                receipt.verdict(),
                receipt.verdictSummary(),
                plainSummary,
                limitations,
                receipt.blockingReasons(),
                receipt.warnings(),
                receipt.inputHashes(),
                receipt.toolchain(),
                receipt.stages(),
                receipt.reproduction(),
                receipt.baselineRegression(),
                receipt.patchedRegression(),
                receipt.edgeCases(),
                receipt.mutation(),
                receipt.scope(),
                "");
    }
}
