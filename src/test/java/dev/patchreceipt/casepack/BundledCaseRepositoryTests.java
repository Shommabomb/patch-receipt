package dev.patchreceipt.casepack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BundledCaseRepositoryTests {

    private final BundledCaseRepository repository = new BundledCaseRepository();

    @Test
    void exposesExactlyThreeHostedCandidates() {
        assertThat(repository.hostedPatches(BundledCaseRepository.DEMO_CASE_ID))
                .extracting(CaseManifest.PatchCandidate::patchId)
                .containsExactlyInAnyOrder(
                        "plausible-distinct",
                        "correct-with-drift",
                        "minimal-robust");
    }

    @Test
    void evaluationPatchesCannotEnterHostedExecution() {
        assertThatThrownBy(() -> repository.loadHosted(
                        BundledCaseRepository.DEMO_CASE_ID,
                        "build-bypass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown hosted patch");
    }

    @Test
    void everyEvaluationCandidateLoadsWithFiveInputHashes() {
        CaseManifest manifest = repository.manifest(BundledCaseRepository.DEMO_CASE_ID);

        assertThat(manifest.patches()).hasSize(6);
        for (CaseManifest.PatchCandidate candidate : manifest.patches()) {
            VerificationCase verificationCase =
                    repository.load(manifest.caseId(), candidate.patchId());
            assertThat(verificationCase.hashes())
                    .containsKeys("manifest", "bugReport", "patch", "project", "verifierPack");
            assertThat(verificationCase.hashes().values())
                    .allSatisfy(hash -> assertThat(hash).hasSize(64));
        }
    }
}
