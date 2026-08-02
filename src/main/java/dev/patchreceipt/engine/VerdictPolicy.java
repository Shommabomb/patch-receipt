package dev.patchreceipt.engine;

import dev.patchreceipt.domain.MutationEvidence;
import dev.patchreceipt.domain.Verdict;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class VerdictPolicy {

    public Decision decide(
            List<String> blockingReasons,
            List<String> warnings,
            MutationEvidence mutation) {
        List<String> finalWarnings = new ArrayList<>(
                warnings == null ? List.of() : warnings);
        if (blockingReasons != null && !blockingReasons.isEmpty()) {
            return new Decision(
                    Verdict.REJECTED,
                    "Mandatory correctness or safety evidence failed.",
                    deduplicate(finalWarnings));
        }

        if (mutation == null || !mutation.processHealthy()) {
            finalWarnings.add("Mutation process did not complete successfully");
        } else if (!mutation.conclusive()) {
            finalWarnings.add("Mutation evidence is inconclusive");
        } else if (mutation.changedLineMutants() < mutation.requiredChangedLineMutants()) {
            finalWarnings.add(
                    "Too few viable changed-line mutants were generated for full verification");
        } else if (!mutation.filesWithoutMutants().isEmpty()) {
            finalWarnings.add(
                    "Some changed production files lack viable changed-line mutation evidence");
        } else if (mutation.changedLineScore() < mutation.requiredScore()) {
            finalWarnings.add("Changed-line mutation score is below the required threshold");
        }

        if (!finalWarnings.isEmpty()) {
            return new Decision(
                    Verdict.PARTIALLY_VERIFIED,
                    "Correctness gates pass, but confidence or scope evidence is incomplete.",
                    deduplicate(finalWarnings));
        }
        return new Decision(
                Verdict.VERIFIED,
                "All correctness, scope, and mutation evidence gates pass.",
                List.of());
    }

    private List<String> deduplicate(List<String> warnings) {
        return List.copyOf(new LinkedHashSet<>(warnings));
    }

    public record Decision(
            Verdict verdict,
            String summary,
            List<String> warnings) {
    }
}
