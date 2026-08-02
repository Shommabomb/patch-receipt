package dev.patchreceipt.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patchreceipt.domain.MutationEvidence;
import dev.patchreceipt.domain.ScopeEvidence;
import dev.patchreceipt.domain.TestEvidence;
import dev.patchreceipt.domain.TestFailure;
import dev.patchreceipt.domain.VerificationReceipt;
import dev.patchreceipt.domain.Verdict;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunRegistryEvidenceSummaryTests {

    @Test
    void rejectedReceiptSurfacesTheExactCounterexampleAndGateFailure() {
        TestFailure counterexample = new TestFailure(
                "CouponContractEdgeCases",
                "generatedContractCases()[3]",
                "AssertionFailedError",
                "case variants are one code ==> expected: <900> but was: <800>");
        VerificationReceipt receipt = receipt(
                Verdict.REJECTED,
                List.of("Independent edge-case suite fails"),
                List.of(),
                new TestEvidence(9, 8, 1, 0, 0, 50, List.of(counterexample)),
                new MutationEvidence(
                        "SKIPPED", false, 0, 0, 0, 0, 0, 0,
                        0, 80, 2, false, List.of(), List.of()),
                new ScopeEvidence(1, 1, 1, List.of(), List.of(), List.of()));

        RunSnapshot.EvidenceSummary summary = RunRegistry.summarizeEvidence(receipt);

        assertThat(summary.metrics())
                .extracting(RunSnapshot.Metric::value)
                .containsExactly("6 / 6", "8 / 9", "Inconclusive", "Clean");
        assertThat(summary.findings())
                .containsExactly(
                        "case variants are one code — expected: <900> but was: <800>",
                        "Independent edge-case suite fails");
    }

    @Test
    void verifiedReceiptShowsCompactMetricsAndAnExplicitCleanFinding() {
        VerificationReceipt receipt = receipt(
                Verdict.VERIFIED,
                List.of(),
                List.of(),
                new TestEvidence(9, 9, 0, 0, 0, 50, List.of()),
                new MutationEvidence(
                        "LIVE_PIT", true, 4, 4, 4, 0, 0, 0,
                        100, 80, 2, true, List.of(), List.of()),
                new ScopeEvidence(1, 9, 1, List.of(), List.of(), List.of()));

        RunSnapshot.EvidenceSummary summary = RunRegistry.summarizeEvidence(receipt);

        assertThat(summary.metrics())
                .extracting(RunSnapshot.Metric::value)
                .containsExactly("6 / 6", "9 / 9", "100% · 4 mutants", "Clean");
        assertThat(summary.findings())
                .containsExactly("No blocking findings or scope warnings.");
    }

    @Test
    void conclusiveMutationEvidenceBelowTheMinimumIsInsufficient() {
        VerificationReceipt receipt = receipt(
                Verdict.PARTIALLY_VERIFIED,
                List.of(),
                List.of("Too few viable changed-line mutants were generated for full verification"),
                new TestEvidence(9, 9, 0, 0, 0, 50, List.of()),
                new MutationEvidence(
                        "LIVE_PIT", true, 1, 1, 1, 0, 0, 0,
                        100, 80, 2, true, List.of(), List.of()),
                new ScopeEvidence(1, 9, 1, List.of(), List.of(), List.of()));

        RunSnapshot.EvidenceSummary summary = RunRegistry.summarizeEvidence(receipt);

        assertThat(summary.metrics())
                .extracting(RunSnapshot.Metric::value)
                .containsExactly("6 / 6", "9 / 9", "Insufficient", "Clean");
    }

    private VerificationReceipt receipt(
            Verdict verdict,
            List<String> blockingReasons,
            List<String> warnings,
            TestEvidence edgeCases,
            MutationEvidence mutation,
            ScopeEvidence scope) {
        TestEvidence regressions = new TestEvidence(6, 6, 0, 0, 0, 40, List.of());
        return new VerificationReceipt(
                1,
                "receipt",
                "0.2.0",
                "2026-07-27T00:00:00Z",
                "2026-07-27T00:00:01Z",
                1_000,
                "checkout-coupons",
                "Checkout coupons",
                "candidate",
                "Candidate",
                verdict,
                "Summary",
                "Plain summary",
                List.of("Limitation"),
                blockingReasons,
                warnings,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                regressions,
                regressions,
                edgeCases,
                mutation,
                scope,
                "");
    }
}
