package dev.patchreceipt.casepack;

import dev.patchreceipt.domain.Verdict;
import java.util.List;

public record CaseManifest(
        int schemaVersion,
        String caseId,
        String title,
        String summary,
        String bugReport,
        Project project,
        Verifier verifier,
        Mutation mutation,
        Scope scope,
        Runtime runtime,
        List<PatchCandidate> patches) {

    public CaseManifest {
        patches = patches == null ? List.of() : List.copyOf(patches);
    }

    public PatchCandidate requirePatch(String patchId) {
        return patches.stream()
                .filter(candidate -> candidate.patchId().equals(patchId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown patch '%s' for case '%s'".formatted(patchId, caseId)));
    }

    public record Project(
            String buildSystem,
            int javaRelease,
            String filesIndex,
            String regressionTest) {
    }

    public record Verifier(
            String filesIndex,
            String reproductionTest,
            String edgeCaseTest,
            String expectedFailureType) {
    }

    public record Mutation(
            List<String> targetClasses,
            List<String> targetTests,
            double minimumChangedLineScore) {

        public Mutation {
            targetClasses = targetClasses == null ? List.of() : List.copyOf(targetClasses);
            targetTests = targetTests == null ? List.of() : List.copyOf(targetTests);
        }
    }

    public record Scope(
            List<String> expectedPaths,
            List<String> forbiddenGlobs,
            int maximumFiles,
            int maximumChangedLines) {

        public Scope {
            expectedPaths = expectedPaths == null ? List.of() : List.copyOf(expectedPaths);
            forbiddenGlobs = forbiddenGlobs == null ? List.of() : List.copyOf(forbiddenGlobs);
        }
    }

    public record Runtime(
            int stageTimeoutSeconds,
            int maximumLogCharacters,
            long maximumWorkspaceBytes) {
    }

    public record PatchCandidate(
            String patchId,
            String title,
            String description,
            String file,
            Verdict expectedVerdict) {
    }
}
