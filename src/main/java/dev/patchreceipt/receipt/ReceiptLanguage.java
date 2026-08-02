package dev.patchreceipt.receipt;

import dev.patchreceipt.domain.MutationEvidence;
import dev.patchreceipt.domain.ScopeEvidence;
import dev.patchreceipt.domain.TestEvidence;
import dev.patchreceipt.domain.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ReceiptLanguage {

    private ReceiptLanguage() {
    }

    public static String plainSummary(
            Verdict verdict,
            TestEvidence edgeCases,
            List<String> blockingReasons) {
        if (verdict == null) {
            return "The verification has not produced a decision yet.";
        }
        return switch (verdict) {
            case VERIFIED ->
                    "Every check PatchReceipt ran passed: the reproduced bug is fixed, "
                            + "the existing tests still pass, the observed file scope is clean, "
                            + "and the mutation score and evidence-count thresholds were met.";
            case PARTIALLY_VERIFIED ->
                    "The correctness checks passed, but at least one confidence or scope "
                            + "check is incomplete. Review the warnings before using this patch.";
            case REJECTED -> rejectedSummary(edgeCases, blockingReasons);
        };
    }

    public static List<String> limitations(
            MutationEvidence mutation,
            ScopeEvidence scope) {
        List<String> limitations = new ArrayList<>();
        limitations.add(
                "PatchReceipt did not review design, security, performance, concurrency, "
                        + "or requirements outside this verifier pack.");
        limitations.add(
                "A clean scope result means no unexpected file changed; "
                        + "it does not prove the implementation is correct.");
        if (mutation != null && mutation.conclusive()) {
            limitations.add(
                    "Mutation testing covered %d viable %s on observed changed lines; "
                            .formatted(
                                    mutation.changedLineMutants(),
                                    mutation.changedLineMutants() == 1 ? "mutant" : "mutants")
                            + "it cannot prove every behaviour.");
        } else {
            limitations.add("Mutation evidence was not conclusive for this run.");
        }
        if (scope != null && !"OBSERVED_FILESYSTEM".equals(scope.provenance())) {
            limitations.add("Scope evidence was not reconciled against observed filesystem changes.");
        }
        return List.copyOf(limitations);
    }

    private static String rejectedSummary(
            TestEvidence edges,
            List<String> blockingReasons) {
        if (edges != null && (edges.failures() > 0 || edges.errors() > 0)) {
            int failed = edges.failures() + edges.errors();
            return ("The reproduced example passed after patching, but %d of %d "
                    + "independent checks failed. The configured verdict is REJECTED.")
                    .formatted(failed, edges.tests());
        }
        String reason = blockingReasons == null || blockingReasons.isEmpty()
                ? "a mandatory verification step failed"
                : blockingReasons.getFirst().toLowerCase(Locale.ROOT);
        return "The configured verdict is REJECTED because %s.".formatted(reason);
    }
}
