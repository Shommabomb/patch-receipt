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

    @Test
    void rendersCanonicalEvidenceWithParityAcrossMarkdownAndHtml() {
        VerificationReceipt receipt = receipt();

        String markdown = markdownRenderer.render(receipt);
        String html = htmlRenderer.render(receipt);

        assertThat(MarkdownReceiptRenderer.class).hasAnnotation(Component.class);
        assertThat(HtmlReceiptRenderer.class).hasAnnotation(Component.class);

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
                "Reproduction before patch",
                "Reproduction after patch",
                "Patched regression",
                "Independent edge cases",
                "80.00%",
                "CheckoutCalculator.java",
                "digest-abc123")) {
            assertThat(markdown).contains(evidence);
            assertThat(html).contains(evidence);
        }

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
                .endsWith("`digest-abc123`\n");
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
                12,
                5,
                4,
                1,
                0,
                0,
                80.0,
                80.0,
                true,
                List.of(finding));

        ScopeEvidence scope = new ScopeEvidence(
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
                1,
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
}
