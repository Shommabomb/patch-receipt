package dev.patchreceipt.scope;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patchreceipt.casepack.CaseManifest;
import dev.patchreceipt.domain.ScopeEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScopeAnalyzerTests {

    private final ScopeAnalyzer analyzer = new ScopeAnalyzer();

    @Test
    void rejectsMalformedPatchWithHunkBeforeFileHeader() {
        ScopeEvidence evidence = analyzer.analyze("""
                @@ -1 +1 @@
                -old
                +new
                """, policy(List.of(), List.of(), 2, 20));

        assertThat(evidence.filesChanged()).isZero();
        assertThat(evidence.hardViolations())
                .containsExactly(
                        "Hunk appears before a file header",
                        "Patch contains no unified diff file headers");
    }

    @Test
    void rejectsBinaryPatchMarkers() {
        ScopeEvidence evidence = analyzer.analyze("""
                diff --git a/src/main/resources/logo.png b/src/main/resources/logo.png
                GIT binary patch
                literal 3
                abc
                """, policy(
                List.of("src/main/resources/logo.png"),
                List.of(),
                2,
                20));

        assertThat(evidence.hardViolations())
                .containsExactly("Binary patches are not allowed");
        assertThat(evidence.filesChanged()).isOne();
    }

    @Test
    void rejectsPathTraversalInEitherDiffPath() {
        ScopeEvidence evidence = analyzer.analyze("""
                diff --git a/src/main/java/App.java b/../outside/App.java
                --- a/src/main/java/App.java
                +++ b/../outside/App.java
                @@ -1 +1 @@
                -old
                +new
                """, policy(List.of(), List.of(), 2, 20));

        assertThat(evidence.hardViolations())
                .containsExactly("Patch path escapes project root: ../outside/App.java");
        assertThat(evidence.warnings())
                .containsExactly("Unexpected path changed: ../outside/App.java");
    }

    @Test
    void rejectsForbiddenPathEvenWhenItIsExpected() {
        ScopeEvidence evidence = analyzer.analyze("""
                diff --git a/pom.xml b/pom.xml
                --- a/pom.xml
                +++ b/pom.xml
                @@ -1 +1 @@
                -<version>1</version>
                +<version>2</version>
                """, policy(
                List.of("pom.xml"),
                List.of("pom.xml", ".github/**"),
                2,
                20));

        assertThat(evidence.hardViolations())
                .containsExactly("Forbidden path changed: pom.xml");
        assertThat(evidence.warnings()).isEmpty();
        assertThat(evidence.files().getFirst().forbidden()).isTrue();
        assertThat(evidence.files().getFirst().expected()).isTrue();
    }

    @Test
    void warnsForUnexpectedPathWithoutTurningItIntoHardFailure() {
        ScopeEvidence evidence = analyzer.analyze("""
                diff --git a/src/main/java/dev/example/Audit.java b/src/main/java/dev/example/Audit.java
                --- a/src/main/java/dev/example/Audit.java
                +++ b/src/main/java/dev/example/Audit.java
                @@ -3 +3 @@
                -return "old";
                +return "new";
                """, policy(
                List.of("src/main/java/dev/example/Calculator.java"),
                List.of("pom.xml"),
                2,
                20));

        assertThat(evidence.hardViolations()).isEmpty();
        assertThat(evidence.warnings())
                .containsExactly(
                        "Unexpected path changed: src/main/java/dev/example/Audit.java");
        assertThat(evidence.files().getFirst().expected()).isFalse();
        assertThat(evidence.files().getFirst().forbidden()).isFalse();
    }

    @Test
    void rejectsPatchExceedingMaximumFileCount() {
        ScopeEvidence evidence = analyzer.analyze("""
                diff --git a/src/main/java/A.java b/src/main/java/A.java
                --- a/src/main/java/A.java
                +++ b/src/main/java/A.java
                @@ -1 +1 @@
                -old
                +new
                diff --git a/src/main/java/B.java b/src/main/java/B.java
                --- a/src/main/java/B.java
                +++ b/src/main/java/B.java
                @@ -1 +1 @@
                -old
                +new
                """, policy(
                List.of("src/main/java/A.java", "src/main/java/B.java"),
                List.of(),
                1,
                20));

        assertThat(evidence.filesChanged()).isEqualTo(2);
        assertThat(evidence.hardViolations())
                .containsExactly("Patch changes 2 files; maximum is 1");
    }

    @Test
    void rejectsPatchExceedingMaximumChangedLineCount() {
        ScopeEvidence evidence = analyzer.analyze("""
                diff --git a/src/main/java/A.java b/src/main/java/A.java
                --- a/src/main/java/A.java
                +++ b/src/main/java/A.java
                @@ -5,2 +5,3 @@
                -oldOne
                -oldTwo
                +newOne
                +newTwo
                +newThree
                """, policy(
                List.of("src/main/java/A.java"),
                List.of(),
                1,
                4));

        assertThat(evidence.additions()).isEqualTo(3);
        assertThat(evidence.deletions()).isEqualTo(2);
        assertThat(evidence.files().getFirst().changedLines()).containsExactly(5, 6, 7);
        assertThat(evidence.hardViolations())
                .containsExactly("Patch changes 5 lines; maximum is 4");
    }

    private CaseManifest.Scope policy(
            List<String> expectedPaths,
            List<String> forbiddenGlobs,
            int maximumFiles,
            int maximumChangedLines) {
        return new CaseManifest.Scope(
                expectedPaths,
                forbiddenGlobs,
                maximumFiles,
                maximumChangedLines);
    }
}
