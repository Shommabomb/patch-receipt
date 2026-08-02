package dev.patchreceipt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.patchreceipt.casepack.BundledCaseRepository;
import dev.patchreceipt.domain.Verdict;
import dev.patchreceipt.engine.VerificationEngine;
import dev.patchreceipt.receipt.JsonReceiptRenderer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "patchreceipt.runner.offline=true",
        // Evaluation measures verdict correctness, not the 90-second production SLO.
        "patchreceipt.runner.total-timeout-seconds=180",
        "patchreceipt.runner.stage-timeout-override-seconds=120"
})
class EvaluationCorpusIntegrationTests {

    @Autowired
    private BundledCaseRepository cases;

    @Autowired
    private VerificationEngine engine;

    @Autowired
    private JsonReceiptRenderer json;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @EnabledIfSystemProperty(named = "patchreceipt.evaluation", matches = "true")
    void allSixGroundTruthedPatchesMatchTheirExpectedVerdict() throws Exception {
        Path output = Path.of("target/evaluation-receipts");
        Files.createDirectories(output);
        var manifest = cases.manifest(BundledCaseRepository.DEMO_CASE_ID);
        List<String> results = new ArrayList<>();
        List<String> mismatches = new ArrayList<>();

        for (var candidate : manifest.patches()) {
            var receipt = engine.verify(cases.load(manifest.caseId(), candidate.patchId()));
            Files.writeString(
                    output.resolve(candidate.patchId() + ".json"),
                    json.render(receipt),
                    StandardCharsets.UTF_8);
            results.add(candidate.patchId() + ": " + receipt.verdict());
            if (receipt.verdict() != candidate.expectedVerdict()) {
                mismatches.add("%s expected %s but received %s"
                        .formatted(
                                candidate.patchId(),
                                candidate.expectedVerdict(),
                                receipt.verdict()));
            }
        }

        assertThat(results).hasSize(6);
        assertThat(results)
                .filteredOn(result -> result.substring(result.indexOf(": ") + 2)
                        .equals(Verdict.VERIFIED.name()))
                .containsExactly("minimal-robust: VERIFIED");
        assertThat(mismatches)
                .as("ground-truth verdict mismatches")
                .isEmpty();
        Files.writeString(
                output.resolve("summary.txt"),
                String.join(System.lineSeparator(), results) + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }

    @Test
    @EnabledIfSystemProperty(named = "patchreceipt.determinism", matches = "true")
    void fiveRobustRunsHaveIdenticalNormalizedEvidence() throws Exception {
        Path output = Path.of("target/determinism-receipts");
        Files.createDirectories(output);
        List<String> normalized = new ArrayList<>();

        for (int run = 1; run <= 5; run++) {
            var receipt = engine.verify(cases.load(
                    BundledCaseRepository.DEMO_CASE_ID,
                    "minimal-robust"));
            String rendered = json.render(receipt);
            Files.writeString(
                    output.resolve("run-" + run + ".json"),
                    rendered,
                    StandardCharsets.UTF_8);
            normalized.add(normalize(rendered));
        }

        assertThat(normalized).allMatch(normalized.getFirst()::equals);
    }

    private String normalize(String rendered) throws Exception {
        JsonNode root = mapper.readTree(rendered);
        removeVolatileEvidence(root);
        return mapper.writeValueAsString(root);
    }

    private void removeVolatileEvidence(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.remove(List.of(
                    "receiptId",
                    "receiptDigest",
                    "startedAt",
                    "completedAt",
                    "durationMs",
                    "sharedInvocationDurationMs",
                    "log"));
            object.elements().forEachRemaining(this::removeVolatileEvidence);
        } else if (node instanceof ArrayNode array) {
            array.elements().forEachRemaining(this::removeVolatileEvidence);
        }
    }
}
