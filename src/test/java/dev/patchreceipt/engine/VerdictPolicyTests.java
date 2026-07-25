package dev.patchreceipt.engine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patchreceipt.domain.MutationEvidence;
import dev.patchreceipt.domain.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerdictPolicyTests {

    private final VerdictPolicy policy = new VerdictPolicy();

    @Test
    void mandatoryFailureAlwaysRejectsEvenWhenOtherEvidencePasses() {
        var decision = policy.decide(
                List.of("Independent edge-case suite fails"),
                List.of(),
                mutation(true, 100, 80));

        assertThat(decision.verdict()).isEqualTo(Verdict.REJECTED);
        assertThat(decision.summary()).contains("Mandatory");
    }

    @Test
    void hardFailureCannotBeDowngradedByIncompleteMutation() {
        var decision = policy.decide(
                List.of("Patch does not compile"),
                List.of("Unexpected path"),
                mutation(false, 0, 80));

        assertThat(decision.verdict()).isEqualTo(Verdict.REJECTED);
        assertThat(decision.warnings()).containsExactly("Unexpected path");
    }

    @Test
    void cleanCorrectnessWithSoftScopeDriftIsPartial() {
        var decision = policy.decide(
                List.of(),
                List.of("Unexpected production path"),
                mutation(true, 100, 80));

        assertThat(decision.verdict()).isEqualTo(Verdict.PARTIALLY_VERIFIED);
    }

    @Test
    void inconclusiveOrBelowThresholdMutationIsPartial() {
        assertThat(policy.decide(
                        List.of(), List.of(), mutation(false, 0, 80)).verdict())
                .isEqualTo(Verdict.PARTIALLY_VERIFIED);
        assertThat(policy.decide(
                        List.of(), List.of(), mutation(true, 79.9, 80)).verdict())
                .isEqualTo(Verdict.PARTIALLY_VERIFIED);
    }

    @Test
    void onlyCompleteCleanEvidenceIsVerified() {
        var decision = policy.decide(
                List.of(),
                List.of(),
                mutation(true, 80, 80));

        assertThat(decision.verdict()).isEqualTo(Verdict.VERIFIED);
        assertThat(decision.warnings()).isEmpty();
    }

    private MutationEvidence mutation(
            boolean conclusive,
            double score,
            double required) {
        return new MutationEvidence(
                "TEST", 1, 1, 1, 0, 0, 0,
                score, required, conclusive, List.of());
    }
}
