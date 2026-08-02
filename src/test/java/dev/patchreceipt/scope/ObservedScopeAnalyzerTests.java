package dev.patchreceipt.scope;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patchreceipt.casepack.CaseManifest;
import dev.patchreceipt.domain.ChangedFile;
import dev.patchreceipt.domain.ScopeEvidence;
import dev.patchreceipt.runner.PatchApplier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObservedScopeAnalyzerTests {

    private final ObservedScopeAnalyzer analyzer = new ObservedScopeAnalyzer();

    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsForbiddenFileThatWasAbsentFromPreflight() throws Exception {
        Path project = temporaryDirectory.resolve("project");
        Files.createDirectories(project.resolve("src/main/java"));
        Files.writeString(project.resolve("src/main/java/App.java"), "old\n");
        Files.writeString(project.resolve("pom.xml"), "safe\n");
        var before = analyzer.capture(project);

        Files.writeString(project.resolve("src/main/java/App.java"), "new\n");
        Files.writeString(project.resolve("pom.xml"), "poisoned\n");

        ScopeEvidence observed = analyzer.reconcile(
                before,
                project,
                policy(2),
                preflight("src/main/java/App.java", 1, 1, Set.of(1)));

        assertThat(observed.provenance()).isEqualTo("OBSERVED_FILESYSTEM");
        assertThat(observed.filesChanged()).isEqualTo(2);
        assertThat(observed.hardViolations())
                .contains(
                        "Forbidden path changed: pom.xml",
                        "Applied patch changed a path absent from preflight: pom.xml");
    }

    @Test
    void detectsHiddenTestEditAndActualFileCap() throws Exception {
        Path project = temporaryDirectory.resolve("many-files");
        Files.createDirectories(project.resolve("src/main/java"));
        Files.createDirectories(project.resolve("src/test/java"));
        Files.writeString(project.resolve("src/main/java/App.java"), "old\n");
        Files.writeString(project.resolve("src/test/java/AppTest.java"), "old\n");
        for (int index = 1; index <= 4; index++) {
            Files.writeString(project.resolve("src/main/java/Extra" + index + ".java"), "old\n");
        }
        var before = analyzer.capture(project);

        Files.writeString(project.resolve("src/main/java/App.java"), "new\n");
        Files.writeString(project.resolve("src/test/java/AppTest.java"), "new\n");
        for (int index = 1; index <= 4; index++) {
            Files.writeString(project.resolve("src/main/java/Extra" + index + ".java"), "new\n");
        }

        ScopeEvidence observed = analyzer.reconcile(
                before,
                project,
                policy(2),
                preflight("src/main/java/App.java", 1, 1, Set.of(1)));

        assertThat(observed.filesChanged()).isEqualTo(6);
        assertThat(observed.hardViolations())
                .contains("Forbidden path changed: src/test/java/AppTest.java")
                .anyMatch(message -> message.equals(
                        "Applied patch changes 6 files; maximum is 2"));
    }

    @Test
    void observedDiffSuppliesCanonicalChangedLines() throws Exception {
        Path project = temporaryDirectory.resolve("changed-lines");
        Path source = project.resolve("src/main/java/App.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "one\n\nthree\n");
        var before = analyzer.capture(project);

        Files.writeString(source, "one\nadded\n\nchanged\nthree\n");

        ScopeEvidence observed = analyzer.reconcile(
                before,
                project,
                policy(2),
                preflight("src/main/java/App.java", 2, 0, Set.of(2, 4)));

        assertThat(observed.hardViolations()).isEmpty();
        assertThat(observed.additions()).isEqualTo(2);
        assertThat(observed.deletions()).isZero();
        assertThat(observed.changedLinesByPath().get("src/main/java/App.java"))
                .containsExactly(2, 4);
    }

    @Test
    void usesObservedCountsWhenEquivalentPatchTextIsNonMinimal() throws Exception {
        Path project = temporaryDirectory.resolve("count-mismatch");
        Path source = project.resolve("src/main/java/App.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "old\n");
        var before = analyzer.capture(project);
        Files.writeString(source, "new\n");

        ScopeEvidence observed = analyzer.reconcile(
                before,
                project,
                policy(2),
                preflight("src/main/java/App.java", 2, 0, Set.of(1, 2)));

        assertThat(observed.hardViolations()).isEmpty();
        assertThat(observed.warnings()).isEmpty();
        assertThat(observed.additions()).isOne();
        assertThat(observed.deletions()).isOne();
    }

    @Test
    void nonMinimalUnifiedDiffCanStillProduceCleanObservedScope() throws Exception {
        Path project = temporaryDirectory.resolve("non-minimal-patch");
        Path source = project.resolve("src/main/java/App.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "one\ntwo\nthree\n");
        var before = analyzer.capture(project);
        String patch = """
                diff --git a/src/main/java/App.java b/src/main/java/App.java
                --- a/src/main/java/App.java
                +++ b/src/main/java/App.java
                @@ -1,3 +1,3 @@
                -one
                +ONE
                -two
                +two
                -three
                +THREE
                """;
        ScopeEvidence preflight =
                new ScopeAnalyzer().analyze(patch, policy(2));

        new PatchApplier().apply(project, patch);
        ScopeEvidence observed =
                analyzer.reconcile(before, project, policy(2), preflight);

        assertThat(preflight.additions()).isEqualTo(3);
        assertThat(preflight.deletions()).isEqualTo(3);
        assertThat(observed.additions()).isEqualTo(2);
        assertThat(observed.deletions()).isEqualTo(2);
        assertThat(observed.hardViolations()).isEmpty();
        assertThat(observed.warnings()).isEmpty();
    }

    @Test
    void ignoresOnlyVerifierCreatedGitMetadata() throws Exception {
        Path project = temporaryDirectory.resolve("git-metadata");
        Path source = project.resolve("src/main/java/App.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "old\n");
        var before = analyzer.capture(project);

        Files.writeString(source, "new\n");
        Files.createDirectories(project.resolve(".git/objects"));
        Files.writeString(project.resolve(".git/config"), "verifier metadata\n");
        Files.write(project.resolve(".git/objects/index"), new byte[] {0, 1, 2});

        ScopeEvidence observed = analyzer.reconcile(
                before,
                project,
                policy(2),
                preflight("src/main/java/App.java", 1, 1, Set.of(1)));

        assertThat(observed.hardViolations()).isEmpty();
        assertThat(observed.files())
                .extracting(ChangedFile::path)
                .containsExactly("src/main/java/App.java");
    }

    private ScopeEvidence preflight(
            String path,
            int additions,
            int deletions,
            Set<Integer> changedLines) {
        return new ScopeEvidence(
                "PATCH_PREFLIGHT",
                1,
                additions,
                deletions,
                List.of(new ChangedFile(
                        path,
                        additions,
                        deletions,
                        true,
                        false,
                        changedLines)),
                List.of(),
                List.of());
    }

    private CaseManifest.Scope policy(int maximumFiles) {
        return new CaseManifest.Scope(
                List.of("src/main/java/App.java"),
                List.of("pom.xml", "src/test/**"),
                maximumFiles,
                50);
    }
}
