package dev.patchreceipt.engine;

import dev.patchreceipt.casepack.CaseManifest;
import dev.patchreceipt.casepack.VerificationCase;
import dev.patchreceipt.domain.MutationEvidence;
import dev.patchreceipt.domain.ReproductionEvidence;
import dev.patchreceipt.domain.ScopeEvidence;
import dev.patchreceipt.domain.StageResult;
import dev.patchreceipt.domain.StageStatus;
import dev.patchreceipt.domain.TestEvidence;
import dev.patchreceipt.domain.Verdict;
import dev.patchreceipt.domain.VerificationReceipt;
import dev.patchreceipt.parsers.PitestReportParser;
import dev.patchreceipt.parsers.SurefireReportParser;
import dev.patchreceipt.receipt.ReceiptDigestService;
import dev.patchreceipt.runner.MavenRunner;
import dev.patchreceipt.runner.PatchApplier;
import dev.patchreceipt.runner.ProcessResult;
import dev.patchreceipt.scope.ScopeAnalyzer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class VerificationEngine {

    public static final String ENGINE_VERSION = "0.1.0";

    private final ScopeAnalyzer scopeAnalyzer;
    private final WorkspaceManager workspaceManager;
    private final MavenRunner mavenRunner;
    private final PatchApplier patchApplier;
    private final SurefireReportParser surefireParser;
    private final PitestReportParser pitestParser;
    private final ReceiptDigestService digestService;

    public VerificationEngine(
            ScopeAnalyzer scopeAnalyzer,
            WorkspaceManager workspaceManager,
            MavenRunner mavenRunner,
            PatchApplier patchApplier,
            SurefireReportParser surefireParser,
            PitestReportParser pitestParser,
            ReceiptDigestService digestService) {
        this.scopeAnalyzer = scopeAnalyzer;
        this.workspaceManager = workspaceManager;
        this.mavenRunner = mavenRunner;
        this.patchApplier = patchApplier;
        this.surefireParser = surefireParser;
        this.pitestParser = pitestParser;
        this.digestService = digestService;
    }

    public VerificationReceipt verify(VerificationCase verificationCase) {
        return verify(verificationCase, ProgressListener.NONE);
    }

    public VerificationReceipt verify(
            VerificationCase verificationCase,
            ProgressListener listener) {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        String receiptId = UUID.randomUUID().toString();
        List<StageResult> stages = new ArrayList<>();
        List<String> blockingReasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        ScopeEvidence scope = scopeAnalyzer.analyze(
                verificationCase.patch(), verificationCase.manifest().scope());
        TestEvidence baselineRegression = null;
        TestEvidence patchedRegression = null;
        TestEvidence baselineReproduction = null;
        TestEvidence patchedReproduction = null;
        TestEvidence edgeCases = null;
        MutationEvidence mutation = unavailableMutation(verificationCase.manifest());
        Path workspace = null;

        try {
            StageResult scopeStage = stage(
                    "scope-preflight",
                    "Patch preflight",
                    scope.hasHardViolations() ? StageStatus.FAIL
                            : scope.hasWarnings() ? StageStatus.WARN : StageStatus.PASS,
                    0,
                    scope.hasHardViolations()
                            ? "Patch violates the hosted scope policy."
                            : scope.hasWarnings()
                                    ? "Patch is executable but changes unexpected production scope."
                                    : "Patch stays within the declared production scope.",
                    Map.of(
                            "filesChanged", scope.filesChanged(),
                            "additions", scope.additions(),
                            "deletions", scope.deletions()),
                    String.join(System.lineSeparator(), concat(
                            scope.hardViolations(), scope.warnings())));
            record(stages, listener, scopeStage);

            if (scope.hasHardViolations()) {
                blockingReasons.addAll(scope.hardViolations());
                return receipt(
                        verificationCase, receiptId, startedAt, startedNanos, stages,
                        blockingReasons, warnings, scope, null, null, null, null, mutation);
            }
            warnings.addAll(scope.warnings());

            workspace = workspaceManager.create(receiptId);
            Path baseline = workspace.resolve("baseline");
            Path patched = workspace.resolve("patched");
            Files.createDirectories(baseline);
            Files.createDirectories(patched);
            verificationCase.materializeProject(baseline);
            verificationCase.materializeProject(patched);

            CaseManifest manifest = verificationCase.manifest();
            Duration timeout = Duration.ofSeconds(manifest.runtime().stageTimeoutSeconds());
            int maxLog = manifest.runtime().maximumLogCharacters();

            verificationCase.injectVerifier(baseline);
            String baselineTestSelection = String.join(
                    ",",
                    manifest.project().regressionTest(),
                    manifest.verifier().reproductionTest());
            ProcessResult baselineRun = mavenRunner.run(
                    baseline,
                    List.of("-q", "-Dtest=" + baselineTestSelection, "test"),
                    timeout,
                    maxLog);
            baselineRegression = surefireParser.parse(
                    baseline, manifest.project().regressionTest(), baselineRun.durationMs());
            baselineReproduction = surefireParser.parse(
                    baseline, manifest.verifier().reproductionTest(), baselineRun.durationMs());
            boolean baselineProcessHealthy = !baselineRun.timedOut()
                    && baselineRegression.tests() > 0
                    && baselineReproduction.tests() > 0;
            boolean baselineHealthy = baselineProcessHealthy && baselineRegression.successful();
            record(stages, listener, stage(
                    "baseline-regression",
                    "Baseline health",
                    baselineHealthy ? StageStatus.PASS : StageStatus.FAIL,
                    baselineRun.durationMs(),
                    baselineHealthy
                            ? "Original regression suite passes in the shared baseline-test run."
                            : "The unpatched project is not a healthy verification baseline.",
                    sharedTestMetrics(baselineRegression, baselineRun.durationMs()),
                    ""));
            if (!baselineHealthy) {
                blockingReasons.add("Baseline regression suite is not healthy");
                return receipt(
                        verificationCase, receiptId, startedAt, startedNanos, stages,
                        blockingReasons, warnings, scope, null, baselineRegression,
                        null, null, mutation);
            }

            boolean reproduced = expectedReproduction(
                    baselineReproduction,
                    baselineRun,
                    manifest.verifier().expectedFailureType());
            record(stages, listener, stage(
                    "baseline-reproduction",
                    "Reproduce the bug",
                    reproduced ? StageStatus.PASS : StageStatus.FAIL,
                    0,
                    reproduced
                            ? "The sealed reproduction fails by the expected assertion in the shared baseline-test run."
                            : "The expected bug was not validly reproduced.",
                    sharedTestMetrics(baselineReproduction, baselineRun.durationMs()),
                    workspaceManager.sanitize(baselineRun.output(), workspace)));
            if (!reproduced) {
                blockingReasons.add("The expected bug was not validly reproduced");
                return receipt(
                        verificationCase, receiptId, startedAt, startedNanos, stages,
                        blockingReasons, warnings, scope,
                        new ReproductionEvidence(
                                manifest.verifier().reproductionTest(),
                                manifest.verifier().expectedFailureType(),
                                false, baselineReproduction, null),
                        baselineRegression, null, null, mutation);
            }

            long applyStarted = System.nanoTime();
            try {
                patchApplier.apply(patched, verificationCase.patch());
                record(stages, listener, stage(
                        "apply-patch",
                        "Apply the patch",
                        StageStatus.PASS,
                        elapsed(applyStarted),
                        "Unified diff applied to a fresh project copy.",
                        Map.of("patchSha256", verificationCase.hashes().get("patch")),
                        ""));
            } catch (Exception exception) {
                record(stages, listener, stage(
                        "apply-patch",
                        "Apply the patch",
                        StageStatus.FAIL,
                        elapsed(applyStarted),
                        "The unified diff could not be applied.",
                        Map.of(),
                        exception.getMessage()));
                blockingReasons.add("Patch could not be applied");
                return receipt(
                        verificationCase, receiptId, startedAt, startedNanos, stages,
                        blockingReasons, warnings, scope,
                        new ReproductionEvidence(
                                manifest.verifier().reproductionTest(),
                                manifest.verifier().expectedFailureType(),
                                true, baselineReproduction, null),
                        baselineRegression, null, null, mutation);
            }

            verificationCase.injectVerifier(patched);
            String patchedTestSelection = String.join(
                    ",",
                    manifest.verifier().reproductionTest(),
                    manifest.project().regressionTest(),
                    manifest.verifier().edgeCaseTest());
            ProcessResult patchedTestRun = mavenRunner.run(
                    patched,
                    List.of("-q", "-Dtest=" + patchedTestSelection, "test"),
                    timeout,
                    maxLog);
            patchedReproduction = surefireParser.parse(
                    patched,
                    manifest.verifier().reproductionTest(),
                    patchedTestRun.durationMs());
            patchedRegression = surefireParser.parse(
                    patched,
                    manifest.project().regressionTest(),
                    patchedTestRun.durationMs());
            edgeCases = surefireParser.parse(
                    patched,
                    manifest.verifier().edgeCaseTest(),
                    patchedTestRun.durationMs());

            boolean patchedTestProcessHealthy = !patchedTestRun.timedOut()
                    && patchedReproduction.tests() > 0
                    && patchedRegression.tests() > 0
                    && edgeCases.tests() > 0;
            boolean fixed = patchedTestProcessHealthy && patchedReproduction.successful();
            record(stages, listener, stage(
                    "patched-reproduction",
                    "Verify the fix",
                    fixed ? StageStatus.PASS : StageStatus.FAIL,
                    patchedTestRun.durationMs(),
                    fixed
                            ? "The same sealed reproduction now passes in the shared patched-test run."
                            : "The patch does not fix the reproduced behavior.",
                    sharedTestMetrics(patchedReproduction, patchedTestRun.durationMs()),
                    workspaceManager.sanitize(patchedTestRun.output(), workspace)));
            if (!fixed) {
                blockingReasons.add("Reproduction test still fails after patching");
            }

            boolean regressionsPass = patchedTestProcessHealthy && patchedRegression.successful();
            record(stages, listener, stage(
                    "patched-regression",
                    "Run unchanged regressions",
                    regressionsPass ? StageStatus.PASS : StageStatus.FAIL,
                    0,
                    regressionsPass
                            ? "All original tests pass unchanged in the shared patched-test run."
                            : "The patch breaks at least one original regression.",
                    sharedTestMetrics(patchedRegression, patchedTestRun.durationMs()),
                    ""));
            if (!regressionsPass) {
                blockingReasons.add("Original regression suite fails after patching");
            }

            boolean edgesPass = patchedTestProcessHealthy && edgeCases.successful();
            record(stages, listener, stage(
                    "edge-cases",
                    "Generate independent edge cases",
                    edgesPass ? StageStatus.PASS : StageStatus.FAIL,
                    0,
                    edgesPass
                            ? "%d sealed dynamic edge cases pass in the shared patched-test run."
                                    .formatted(edgeCases.tests())
                            : "%d independent edge cases fail.".formatted(
                                    edgeCases.failures() + edgeCases.errors()),
                    sharedTestMetrics(edgeCases, patchedTestRun.durationMs()),
                    ""));
            if (!edgesPass) {
                blockingReasons.add("Independent edge-case suite fails");
            }

            if (blockingReasons.isEmpty()) {
                ProcessResult mutationRun = mavenRunner.run(
                        patched,
                        List.of("-q", "org.pitest:pitest-maven:mutationCoverage"),
                        timeout,
                        maxLog);
                mutation = pitestParser.parse(
                        patched,
                        scope.changedLinesByPath(),
                        manifest.mutation().minimumChangedLineScore());
                boolean mutationPass = mutationRun.successful()
                        && mutation.conclusive()
                        && mutation.changedLineScore() >= mutation.requiredScore();
                StageStatus mutationStatus = mutationPass ? StageStatus.PASS : StageStatus.WARN;
                record(stages, listener, stage(
                        "mutation",
                        "Challenge the evidence",
                        mutationStatus,
                        mutationRun.durationMs(),
                        mutationPass
                                ? "Changed-line mutation score is %.1f%%."
                                        .formatted(mutation.changedLineScore())
                                : mutation.conclusive()
                                        ? "Mutation score %.1f%% is below the %.1f%% gate."
                                                .formatted(
                                                        mutation.changedLineScore(),
                                                        mutation.requiredScore())
                                        : "Mutation evidence is inconclusive.",
                        mutationMetrics(mutation),
                        workspaceManager.sanitize(mutationRun.output(), workspace)));
            } else {
                record(stages, listener, stage(
                        "mutation",
                        "Challenge the evidence",
                        StageStatus.SKIPPED,
                        0,
                        "Mutation testing was skipped because correctness gates failed.",
                        Map.of(),
                        ""));
            }

            long workspaceBytes = workspaceManager.size(workspace);
            if (workspaceBytes > manifest.runtime().maximumWorkspaceBytes()) {
                blockingReasons.add("Verification workspace exceeded its size limit");
                record(stages, listener, stage(
                        "workspace-limit",
                        "Workspace limit",
                        StageStatus.FAIL,
                        0,
                        "Verification workspace exceeded its configured byte limit.",
                        Map.of(
                                "actualBytes", workspaceBytes,
                                "maximumBytes", manifest.runtime().maximumWorkspaceBytes()),
                        ""));
            }

            ReproductionEvidence reproduction = new ReproductionEvidence(
                    manifest.verifier().reproductionTest(),
                    manifest.verifier().expectedFailureType(),
                    true,
                    baselineReproduction,
                    patchedReproduction);
            return receipt(
                    verificationCase, receiptId, startedAt, startedNanos, stages,
                    blockingReasons, warnings, scope, reproduction, baselineRegression,
                    patchedRegression, edgeCases, mutation);
        } catch (Exception exception) {
            blockingReasons.add("Verification engine error: " + safeMessage(exception));
            record(stages, listener, stage(
                    "engine-error",
                    "Verification engine",
                    StageStatus.ERROR,
                    0,
                    "The verification run ended unexpectedly.",
                    Map.of("exception", exception.getClass().getSimpleName()),
                    safeMessage(exception)));
            return receipt(
                    verificationCase, receiptId, startedAt, startedNanos, stages,
                    blockingReasons, warnings, scope,
                    baselineReproduction == null ? null : new ReproductionEvidence(
                            verificationCase.manifest().verifier().reproductionTest(),
                            verificationCase.manifest().verifier().expectedFailureType(),
                            expectedReproductionObserved(baselineReproduction),
                            baselineReproduction,
                            patchedReproduction),
                    baselineRegression, patchedRegression, edgeCases, mutation);
        } finally {
            workspaceManager.cleanup(workspace);
        }
    }

    private VerificationReceipt receipt(
            VerificationCase verificationCase,
            String receiptId,
            Instant startedAt,
            long startedNanos,
            List<StageResult> stages,
            List<String> blockingReasons,
            List<String> warnings,
            ScopeEvidence scope,
            ReproductionEvidence reproduction,
            TestEvidence baselineRegression,
            TestEvidence patchedRegression,
            TestEvidence edgeCases,
            MutationEvidence mutation) {
        Verdict verdict;
        String summary;
        List<String> finalWarnings = new ArrayList<>(warnings);
        if (!blockingReasons.isEmpty()) {
            verdict = Verdict.REJECTED;
            summary = "Mandatory correctness or safety evidence failed.";
        } else {
            if (!mutation.conclusive()) {
                finalWarnings.add("Mutation evidence is inconclusive");
            } else if (mutation.changedLineScore() < mutation.requiredScore()) {
                finalWarnings.add("Changed-line mutation score is below the required threshold");
            }
            if (!finalWarnings.isEmpty()) {
                verdict = Verdict.PARTIALLY_VERIFIED;
                summary = "Correctness gates pass, but confidence or scope evidence is incomplete.";
            } else {
                verdict = Verdict.VERIFIED;
                summary = "All correctness, scope, and mutation evidence gates pass.";
            }
        }

        Instant completedAt = Instant.now();
        VerificationReceipt receipt = new VerificationReceipt(
                1,
                receiptId,
                ENGINE_VERSION,
                startedAt.toString(),
                completedAt.toString(),
                elapsed(startedNanos),
                verificationCase.manifest().caseId(),
                verificationCase.manifest().title(),
                verificationCase.candidate().patchId(),
                verificationCase.candidate().title(),
                verdict,
                summary,
                List.copyOf(blockingReasons),
                List.copyOf(new java.util.LinkedHashSet<>(finalWarnings)),
                verificationCase.hashes(),
                Map.of(
                        "java", System.getProperty("java.version"),
                        "maven", "3.9.16",
                        "pitest", "1.25.4",
                        "operatingSystem", System.getProperty("os.name")),
                stages,
                reproduction,
                baselineRegression,
                patchedRegression,
                edgeCases,
                mutation,
                scope,
                "");
        return digestService.attachDigest(receipt);
    }

    private boolean expectedReproduction(
            TestEvidence evidence,
            ProcessResult process,
            String expectedFailureType) {
        return !process.timedOut()
                && process.exitCode() != 0
                && evidence.tests() == 1
                && evidence.failures() == 1
                && evidence.errors() == 0
                && evidence.failureDetails().stream()
                        .anyMatch(failure -> failure.type().equals(expectedFailureType)
                                || failure.type().endsWith(
                                        expectedFailureType.substring(
                                                expectedFailureType.lastIndexOf('.') + 1)));
    }

    private boolean expectedReproductionObserved(TestEvidence evidence) {
        return evidence != null && evidence.tests() == 1
                && evidence.failures() == 1 && evidence.errors() == 0;
    }

    private MutationEvidence unavailableMutation(CaseManifest manifest) {
        return new MutationEvidence(
                "NOT_RUN", 0, 0, 0, 0, 0, 0, 0.0,
                manifest.mutation().minimumChangedLineScore(), false, List.of());
    }

    private Map<String, Object> testMetrics(TestEvidence evidence) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("tests", evidence.tests());
        metrics.put("passed", evidence.passed());
        metrics.put("failures", evidence.failures());
        metrics.put("errors", evidence.errors());
        metrics.put("skipped", evidence.skipped());
        return metrics;
    }

    private Map<String, Object> sharedTestMetrics(
            TestEvidence evidence,
            long sharedRunDurationMs) {
        Map<String, Object> metrics = new LinkedHashMap<>(testMetrics(evidence));
        metrics.put("sharedInvocation", true);
        metrics.put("sharedInvocationDurationMs", sharedRunDurationMs);
        return metrics;
    }

    private Map<String, Object> mutationMetrics(MutationEvidence evidence) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalMutants", evidence.totalMutants());
        metrics.put("changedLineMutants", evidence.changedLineMutants());
        metrics.put("killed", evidence.killed());
        metrics.put("survived", evidence.survived());
        metrics.put("uncovered", evidence.uncovered());
        metrics.put("score", evidence.changedLineScore());
        metrics.put("requiredScore", evidence.requiredScore());
        return metrics;
    }

    private StageResult stage(
            String id,
            String title,
            StageStatus status,
            long durationMs,
            String summary,
            Map<String, Object> metrics,
            String log) {
        return new StageResult(id, title, status, durationMs, summary, metrics, log);
    }

    private void record(
            List<StageResult> stages,
            ProgressListener listener,
            StageResult stage) {
        stages.add(stage);
        listener.stageCompleted(stage);
    }

    private long elapsed(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private List<String> concat(List<String> first, List<String> second) {
        List<String> values = new ArrayList<>(first);
        values.addAll(second);
        return values;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
