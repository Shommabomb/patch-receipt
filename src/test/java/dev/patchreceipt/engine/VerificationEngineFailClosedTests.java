package dev.patchreceipt.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patchreceipt.casepack.CaseManifest;
import dev.patchreceipt.casepack.VerificationCase;
import dev.patchreceipt.domain.ChangedFile;
import dev.patchreceipt.domain.ScopeEvidence;
import dev.patchreceipt.domain.TestEvidence;
import dev.patchreceipt.domain.TestFailure;
import dev.patchreceipt.domain.Verdict;
import dev.patchreceipt.parsers.PitestReportParser;
import dev.patchreceipt.parsers.SurefireReportParser;
import dev.patchreceipt.receipt.EvidenceSanitizer;
import dev.patchreceipt.receipt.ReceiptDigestService;
import dev.patchreceipt.runner.MavenRunner;
import dev.patchreceipt.runner.PatchApplier;
import dev.patchreceipt.runner.ProcessResult;
import dev.patchreceipt.scope.ObservedScopeAnalyzer;
import dev.patchreceipt.scope.ScopeAnalyzer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerificationEngineFailClosedTests {

    private static final String REGRESSION = "dev.example.RegressionTest";
    private static final String REPRODUCTION = "dev.example.ReproductionTest";
    private static final String EDGES = "dev.example.EdgeCases";
    private static final String EXPECTED_FAILURE =
            "org.opentest4j.AssertionFailedError";
    private static final String SOURCE_PATH =
            "src/main/java/dev/example/Calculator.java";

    @TempDir
    Path temporaryDirectory;

    @Test
    void sharedMavenExitReflectsIndividualReportedSuiteResults() {
        TestEvidence pass = passing(1);
        TestEvidence edgeFailure =
                new TestEvidence(1, 0, 1, 0, 0, 1, List.of());
        TestEvidence skipped =
                new TestEvidence(1, 0, 0, 0, 1, 1, List.of());

        assertThat(VerificationEngine.combinedTestProcessHealthy(
                        new ProcessResult(1, false, 1, "", false),
                        pass,
                        pass,
                        edgeFailure))
                .isTrue();
        assertThat(VerificationEngine.combinedTestProcessHealthy(
                        new ProcessResult(1, false, 1, "", false),
                        pass,
                        pass,
                        pass))
                .isFalse();
        assertThat(VerificationEngine.combinedTestProcessHealthy(
                        new ProcessResult(0, false, 1, "", false),
                        pass,
                        pass,
                        skipped))
                .isFalse();
    }

    @Test
    void timedOutPitProcessIgnoresParseablePerfectReport() throws Exception {
        ProcessResult timeout = new ProcessResult(-1, true, 45_000, "timed out", false);
        Harness harness = harness(EXPECTED_FAILURE, timeout);
        provePerfectReportExists(harness.patchedProject());

        var receipt = harness.engine().verify(harness.verificationCase());

        assertThat(receipt.verdict()).isEqualTo(Verdict.PARTIALLY_VERIFIED);
        assertThat(receipt.mutation().processHealthy()).isFalse();
        assertThat(receipt.mutation().provenance()).isEqualTo("LIVE_TIMEOUT");
        assertThat(receipt.stages())
                .filteredOn(stage -> stage.id().equals("mutation"))
                .singleElement()
                .satisfies(stage -> assertThat(stage.summary())
                        .isEqualTo(
                                "Mutation testing timed out; partial evidence was not accepted."));
        verify(harness.pitestParser(), never())
                .parse(any(Path.class), any(), anyDouble(), anyInt());
    }

    @Test
    void nonZeroPitProcessIgnoresParseablePerfectReport() throws Exception {
        ProcessResult failed = new ProcessResult(2, false, 500, "pit failed", false);
        Harness harness = harness(EXPECTED_FAILURE, failed);
        provePerfectReportExists(harness.patchedProject());

        var receipt = harness.engine().verify(harness.verificationCase());

        assertThat(receipt.verdict()).isEqualTo(Verdict.PARTIALLY_VERIFIED);
        assertThat(receipt.mutation().processHealthy()).isFalse();
        assertThat(receipt.mutation().provenance()).isEqualTo("LIVE_PROCESS_FAILED");
        assertThat(receipt.stages())
                .filteredOn(stage -> stage.id().equals("mutation"))
                .singleElement()
                .satisfies(stage -> assertThat(stage.summary())
                        .isEqualTo(
                                "Mutation testing exited unsuccessfully; "
                                        + "its report was not accepted."));
        verify(harness.pitestParser(), never())
                .parse(any(Path.class), any(), anyDouble(), anyInt());
    }

    @Test
    void similarlyNamedFailureClassDoesNotReproduceConfiguredFailure() throws Exception {
        Harness harness = harness(
                "dev.attacker.AssertionFailedError",
                new ProcessResult(0, false, 100, "", false));

        var receipt = harness.engine().verify(harness.verificationCase());

        assertThat(receipt.verdict()).isEqualTo(Verdict.REJECTED);
        assertThat(receipt.reproduction().expectedBaselineFailureObserved()).isFalse();
        assertThat(receipt.blockingReasons())
                .containsExactly("The expected bug was not validly reproduced");
        verify(harness.patchApplier(), never()).apply(any(Path.class), anyString());
    }

    @Test
    void patchedTimeoutReasonDoesNotClaimTestsFailed() {
        String reason = VerificationEngine.patchedSuiteFailureReason(
                new ProcessResult(-1, true, 60_000, "", false),
                passing(1),
                "Independent edge-case suite fails",
                "The patched run did not execute the independent edge-case suite");

        assertThat(reason)
                .isEqualTo(
                        "Patched test run exceeded the time budget before correctness could be established")
                .doesNotContain("fails");
    }

    private Harness harness(
            String observedFailureType,
            ProcessResult mutationResult) throws Exception {
        ScopeAnalyzer scopeAnalyzer = mock(ScopeAnalyzer.class);
        ObservedScopeAnalyzer observedScope = mock(ObservedScopeAnalyzer.class);
        WorkspaceManager workspaces = mock(WorkspaceManager.class);
        MavenRunner maven = mock(MavenRunner.class);
        PatchApplier patchApplier = mock(PatchApplier.class);
        SurefireReportParser surefire = mock(SurefireReportParser.class);
        PitestReportParser pitest = mock(PitestReportParser.class);

        CaseManifest manifest = manifest();
        CaseManifest.PatchCandidate candidate = manifest.patches().getFirst();
        VerificationCase verificationCase = new VerificationCase(
                manifest,
                candidate,
                "bug",
                "diff",
                Map.of(),
                Map.of(),
                Map.of(
                        "manifest", "manifest-hash",
                        "bugReport", "bug-hash",
                        "patch", "patch-hash",
                        "project", "project-hash",
                        "verifierPack", "verifier-hash"));

        ScopeEvidence preflight = scope("PATCH_PREFLIGHT");
        ScopeEvidence observed = scope("OBSERVED_FILESYSTEM");
        Path workspace = temporaryDirectory.resolve("workspace-" + mutationResult.exitCode()
                + "-" + mutationResult.timedOut() + "-" + observedFailureType.hashCode());
        Path patched = workspace.resolve("patched");

        when(scopeAnalyzer.analyze("diff", manifest.scope())).thenReturn(preflight);
        when(workspaces.create(anyString())).thenReturn(workspace);
        when(workspaces.size(workspace)).thenReturn(0L);
        when(observedScope.capture(patched))
                .thenReturn(new ObservedScopeAnalyzer.TreeSnapshot(Map.of()));
        when(observedScope.reconcile(
                        any(ObservedScopeAnalyzer.TreeSnapshot.class),
                        eq(patched),
                        eq(manifest.scope()),
                        eq(preflight)))
                .thenReturn(observed);

        ProcessResult baseline = new ProcessResult(1, false, 100, "", false);
        ProcessResult patchedTests = new ProcessResult(0, false, 100, "", false);
        when(maven.run(
                        any(Path.class),
                        anyList(),
                        any(Duration.class),
                        anyInt()))
                .thenReturn(baseline, patchedTests, mutationResult);

        TestEvidence regression = passing(6);
        TestEvidence baselineReproduction = new TestEvidence(
                1,
                0,
                1,
                0,
                0,
                100,
                List.of(new TestFailure(
                        REPRODUCTION,
                        "reproduces",
                        observedFailureType,
                        "expected failure")));
        when(surefire.parse(any(Path.class), eq(REGRESSION), anyLong()))
                .thenReturn(regression);
        when(surefire.parse(any(Path.class), eq(REPRODUCTION), anyLong()))
                .thenReturn(baselineReproduction, passing(1));
        when(surefire.parse(any(Path.class), eq(EDGES), anyLong()))
                .thenReturn(passing(9));

        VerificationEngine engine = new VerificationEngine(
                scopeAnalyzer,
                observedScope,
                workspaces,
                maven,
                patchApplier,
                surefire,
                pitest,
                new ReceiptDigestService(),
                new EvidenceSanitizer(
                        temporaryDirectory.toString(),
                        temporaryDirectory.resolve(".cache/maven").toString(),
                        temporaryDirectory.resolve("home").toString()),
                new VerdictPolicy(),
                60,
                0);
        return new Harness(engine, verificationCase, pitest, patchApplier, patched);
    }

    private void provePerfectReportExists(Path patched) throws Exception {
        Path reports = Files.createDirectories(patched.resolve("target/pit-reports"));
        Files.writeString(reports.resolve("mutations.xml"), """
                <mutations>
                  <mutation status="KILLED">
                    <sourceFile>Calculator.java</sourceFile>
                    <mutatedClass>dev.example.Calculator</mutatedClass>
                    <mutatedMethod>calculate</mutatedMethod>
                    <methodDescription>()I</methodDescription>
                    <lineNumber>8</lineNumber>
                    <mutator>MathMutator</mutator>
                    <indexes><index>0</index></indexes>
                    <blocks><block>0</block></blocks>
                    <killingTest>dev.example.EdgeCases</killingTest>
                    <description>changed arithmetic</description>
                  </mutation>
                </mutations>
                """);

        assertThat(new PitestReportParser()
                        .parse(patched, Map.of(SOURCE_PATH, Set.of(8)), 80, 2)
                        .changedLineScore())
                .isEqualTo(100);
    }

    private CaseManifest manifest() {
        return new CaseManifest(
                1,
                "test-case",
                "Test case",
                "summary",
                "bug.md",
                new CaseManifest.Project("MAVEN", 21, "project.txt", REGRESSION),
                new CaseManifest.Verifier(
                        "verifier.txt",
                        REPRODUCTION,
                        EDGES,
                        EXPECTED_FAILURE),
                new CaseManifest.Mutation(
                        List.of("dev.example.Calculator"),
                        List.of("dev.example.*"),
                        80,
                        2),
                new CaseManifest.Scope(
                        List.of(SOURCE_PATH),
                        List.of("pom.xml", "src/test/**"),
                        2,
                        50),
                new CaseManifest.Runtime(45, 24_000, 64_000_000),
                List.of(new CaseManifest.PatchCandidate(
                        "candidate",
                        "Candidate",
                        "description",
                        "candidate.patch",
                        Verdict.PARTIALLY_VERIFIED)));
    }

    private ScopeEvidence scope(String provenance) {
        return new ScopeEvidence(
                provenance,
                1,
                1,
                1,
                List.of(new ChangedFile(
                        SOURCE_PATH,
                        1,
                        1,
                        true,
                        false,
                        Set.of(8))),
                List.of(),
                List.of());
    }

    private TestEvidence passing(int tests) {
        return new TestEvidence(tests, tests, 0, 0, 0, 100, List.of());
    }

    private record Harness(
            VerificationEngine engine,
            VerificationCase verificationCase,
            PitestReportParser pitestParser,
            PatchApplier patchApplier,
            Path patchedProject) {
    }
}
