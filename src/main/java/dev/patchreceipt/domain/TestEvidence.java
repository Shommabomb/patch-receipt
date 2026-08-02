package dev.patchreceipt.domain;

import java.util.List;

public record TestEvidence(
        int tests,
        int passed,
        int failures,
        int errors,
        int skipped,
        long durationMs,
        List<TestFailure> failureDetails) {

    public TestEvidence {
        failureDetails = failureDetails == null ? List.of() : List.copyOf(failureDetails);
    }

    public boolean successful() {
        return tests > 0
                && passed == tests
                && failures == 0
                && errors == 0
                && skipped == 0;
    }
}
