package dev.patchreceipt.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patchreceipt.casepack.BundledCaseRepository;
import dev.patchreceipt.casepack.VerificationCase;
import dev.patchreceipt.domain.VerificationReceipt;
import dev.patchreceipt.engine.VerificationEngine;
import org.junit.jupiter.api.Test;

class RunRegistryFailureTests {

    @Test
    void completedStateIsNotPublishedBeforeItsReceipt() {
        assertThat(RunRegistry.stableState(RunState.COMPLETED, null))
                .isEqualTo(RunState.RUNNING);
        assertThat(RunRegistry.stableState(
                        RunState.COMPLETED,
                        mock(VerificationReceipt.class)))
                .isEqualTo(RunState.COMPLETED);
    }

    @Test
    void workerFailureAlwaysBecomesATerminalSafeState() throws Exception {
        BundledCaseRepository cases = mock(BundledCaseRepository.class);
        VerificationEngine engine = mock(VerificationEngine.class);
        VerificationCase verificationCase = mock(VerificationCase.class);
        when(cases.loadHosted("checkout-coupons", "minimal-robust"))
                .thenReturn(verificationCase);
        when(engine.verify(eq(verificationCase), any()))
                .thenThrow(new AssertionError(
                        "secret C:\\Users\\reviewer\\project"));

        RunRegistry registry = new RunRegistry(cases, engine);
        try {
            RunSnapshot started =
                    registry.start("checkout-coupons", "minimal-robust");
            RunSnapshot terminal = awaitTerminal(registry, started.runId());

            assertThat(terminal.state()).isEqualTo(RunState.FAILED);
            assertThat(terminal.failureMessage())
                    .isEqualTo(
                            "The verification worker stopped unexpectedly. "
                                    + "Please run the candidate again.")
                    .doesNotContain("reviewer", "secret");
            assertThat(terminal.verdict()).isNull();
            assertThat(terminal.receipts()).isNull();
        } finally {
            registry.close();
        }
    }

    private RunSnapshot awaitTerminal(RunRegistry registry, String runId)
            throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            RunSnapshot snapshot = registry.find(runId);
            if (snapshot.state() == RunState.FAILED
                    || snapshot.state() == RunState.COMPLETED) {
                return snapshot;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Run did not reach a terminal state");
    }
}
