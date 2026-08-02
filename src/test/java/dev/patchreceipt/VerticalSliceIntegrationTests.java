package dev.patchreceipt;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patchreceipt.casepack.BundledCaseRepository;
import dev.patchreceipt.domain.Verdict;
import dev.patchreceipt.engine.VerificationEngine;
import dev.patchreceipt.receipt.JsonReceiptRenderer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "patchreceipt.runner.offline=true",
        // Keep loaded developer machines from turning a correctness test into a latency test.
        "patchreceipt.runner.total-timeout-seconds=180",
        "patchreceipt.runner.stage-timeout-override-seconds=120"
})
class VerticalSliceIntegrationTests {

    @Autowired
    private BundledCaseRepository cases;

    @Autowired
    private VerificationEngine engine;

    @Autowired
    private JsonReceiptRenderer jsonRenderer;

    @Test
    void provesBaselineAppliesRobustPatchAndProducesMutationBackedReceipt() throws Exception {
        var receipt = engine.verify(cases.load("checkout-coupons", "minimal-robust"));

        Files.createDirectories(Path.of("target"));
        Files.writeString(
                Path.of("target/vertical-slice-receipt.json"),
                jsonRenderer.render(receipt),
                StandardCharsets.UTF_8);

        assertThat(receipt.verdict())
                .withFailMessage("Receipt was %s: %s%n%s",
                        receipt.verdict(), receipt.blockingReasons(), receipt.stages())
                .isEqualTo(Verdict.VERIFIED);
        assertThat(receipt.reproduction().expectedBaselineFailureObserved()).isTrue();
        assertThat(receipt.reproduction().patched().successful()).isTrue();
        assertThat(receipt.patchedRegression().successful()).isTrue();
        assertThat(receipt.edgeCases().tests()).isGreaterThanOrEqualTo(8);
        assertThat(receipt.edgeCases().successful()).isTrue();
        assertThat(receipt.mutation().conclusive()).isTrue();
        assertThat(receipt.mutation().changedLineScore()).isGreaterThanOrEqualTo(80.0);
        assertThat(receipt.receiptDigest()).hasSize(64);
    }
}
