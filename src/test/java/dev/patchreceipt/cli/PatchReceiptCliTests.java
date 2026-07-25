package dev.patchreceipt.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PatchReceiptCliTests {

    @Test
    void refusesLocalExecutionWithoutExplicitAcknowledgement() {
        PatchReceiptCli cli = new PatchReceiptCli(null, null, null, null, null);

        int exitCode = cli.execute(new String[] {
                "verify",
                "--project", ".",
                "--bug-report", "bug.md",
                "--patch", "patch.diff",
                "--verifier-pack", "verifier",
                "--output", "output"
        });

        assertThat(exitCode).isEqualTo(2);
    }
}
