package dev.patchreceipt.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patchreceipt.domain.MutationEvidence;
import dev.patchreceipt.domain.ScopeEvidence;
import dev.patchreceipt.domain.TestEvidence;
import dev.patchreceipt.domain.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReceiptLanguageTests {

    @Test
    void rejectedSummaryExplainsTheDecisionWithoutToolJargon() {
        TestEvidence edges = new TestEvidence(9, 6, 3, 0, 0, 0, List.of());

        assertThat(ReceiptLanguage.plainSummary(
                        Verdict.REJECTED,
                        edges,
                        List.of("Independent edge cases fail")))
                .isEqualTo("The reproduced example passed after patching, but 3 of 9 "
                        + "independent checks failed. The configured verdict is REJECTED.");
    }

    @Test
    void verifiedSummaryExplainsTheScopeOfTheDecision() {
        String summary = ReceiptLanguage.plainSummary(
                Verdict.VERIFIED,
                new TestEvidence(9, 9, 0, 0, 0, 0, List.of()),
                List.of());

        assertThat(summary)
                .contains("Every check PatchReceipt ran passed")
                .doesNotContain("coupon")
                .doesNotContain("safe");
    }

    @Test
    void limitationsNameWhatTheEvidenceDoesNotCover() {
        List<String> limitations = ReceiptLanguage.limitations(
                new MutationEvidence(
                        "LIVE", true, 1, 1, 1, 0, 0, 0,
                        100, 80, 1, true, List.of(), List.of()),
                new ScopeEvidence(
                        "OBSERVED_FILESYSTEM",
                        1, 1, 0, List.of(), List.of(), List.of()));

        assertThat(limitations)
                .anyMatch(value -> value.contains("design, security, performance, concurrency"))
                .anyMatch(value -> value.contains("does not prove the implementation is correct"))
                .anyMatch(value -> value.contains("1 viable mutant"));
    }
}
