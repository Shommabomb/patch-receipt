package dev.patchreceipt.engine;

import dev.patchreceipt.casepack.CaseManifest;
import dev.patchreceipt.casepack.VerificationCase;
import dev.patchreceipt.domain.MutationEvidence;
import dev.patchreceipt.domain.ReproductionEvidence;
import dev.patchreceipt.domain.ScopeEvidence;
import dev.patchreceipt.domain.StageResult;
import dev.patchreceipt.domain.StageStatus;
import dev.patchreceipt.domain.TestEvidence;
import dev.patchreceipt.domain.VerificationReceipt;
import dev.patchreceipt.parsers.PitestReportParser;
import dev.patchreceipt.parsers.SurefireReportParser;
import dev.patchreceipt.receipt.ReceiptDigestService;
import dev.patchreceipt.receipt.EvidenceSanitizer;
import dev.patchreceipt.receipt.ReceiptLanguage;
import dev.patchreceipt.runner.MavenRunner;
import dev.patchreceipt.runner.PatchApplier;
import dev.patchreceipt.runner.ProcessResult;
import dev.patchreceipt.scope.ObservedScopeAnalyzer;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class VerificationEngine {

    public static final String ENGINE_VERSION = "0.3.0";
    private final ScopeAnalyzer scopeAnalyzer;
    private final ObservedScopeAnalyzer observedScopeAnalyzer;
    private final WorkspaceManager workspaceManager;
    private final MavenRunner mavenRunner;
    private final PatchApplier patchApplier;
    private final SurefireReportParser surefireParser;
    private final PitestReportParser pitestParser;
    private final ReceiptDigestService digestService;
    private final EvidenceSanitizer evidenceSanitizer;
    private final VerdictPolicy verdictPolicy;
    private final Duration totalRunTimeout;
    private final int stageTimeoutOverrideSeconds;

    public VerificationEngine(
            ScopeAnalyzer scopeAnalyzer,
            ObservedScopeAnalyzer observedScopeAnalyzer,
            WorkspaceManager workspaceManager,
            MavenRunner mavenRunner,
            PatchApplier patchApplier,
            SurefireReportParser surefireParser,
            PitestReportParser pitestParser,
            ReceiptDigestService digestService,
            EvidenceSanitizer evidenceSanitizer,
            VerdictPolicy verdictPolicy,
            @Value("${patchreceipt.runner.total-timeout-seconds:90}")
                    int totalTimeoutSeconds,
            @Value("${patchreceipt.runner.stage-timeout-override-seconds:0}")
                    int stageTimeoutOverrideSeconds) {
        this.scopeAnalyzer = scopeAnalyzer;
        this.observedScopeAnalyzer = observedScopeAnalyzer;
        this.workspaceManager = workspaceManager;
        this.mavenRunner = mavenRunner;
        this.patchApplier = patchApplier;
        this.surefireParser = surefireParser;
        this.pitestParser = pitestParser;
        this.digestService = digestService;
        this.evidenceSanitizer = evidenceSanitizer;
        this.verdictPolicy = verdictPolicy;
        if (totalTimeoutSeconds < 1 || totalTimeoutSeconds > 180) {
            throw new IllegalArgumentException(
                    "Total verification timeout must be between 1 and 180 seconds");
        }
        this.totalRunTimeout = Duration.ofSeconds(totalTimeoutSeconds);
        if (stageTimeoutOverrideSeconds < 0 || stageTimeoutOverrideSeconds > 180) {
            throw new IllegalArgumentException(
                    "Stage timeout override must be between 0 and 180 seconds");
        }
        this.stageTimeoutOverrideSeconds = stageTimeoutOverrideSeconds;
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
                        blockingReasons, warnings, scope, null, null, null, null, mutation,
                        workspace);
            }
            warnings.addAll(scope.warnings());

            workspace = workspaceManager.create(receiptId);
            Path baseline = workspace.resolve("baseline");
            Path patched = workspace.resolve("patched");
            Files.createDirectories(baseline);
            Files.createDirectories(patched);
            verificationCase.materializeProject(baseline);
            verificationCase.materializeProject(patched);
            ObservedScopeAnalyzer.TreeSnapshot patchedBefore =
                    observedScopeAnalyzer.capture(patched);

            CaseManifest manifest = verificationCase.manifest();
            Duration timeout = Duration.ofSeconds(effectiveStageTimeoutSeconds(manifest));
            int maxLog = manifest.runtime().maximumLogCharacters();

            verificationCase.injectVerifier(baseline);
            String baselineTestSelection = String.join(
                    ",",
                    manifest.project().regressionTest(),
                    manifest.verifier().reproductionTest());
            ProcessResult baselineRun = mavenRunner.run(
                    baseline,
                    List.of("-q", "-Dtest=" + baselineTestSelection, "test"),
                    boundedTimeout(startedNanos, timeout),
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
                        null, null, mutation, workspace);
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
                        baselineRegression, null, null, mutation, workspace);
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
                        baselineRegression, null, null, mutation, workspace);
            }

            scope = observedScopeAnalyzer.reconcile(
                    patchedBefore,
                    patched,
                    manifest.scope(),
                    scope);
            record(stages, listener, stage(
                    "scope-observed",
                    "Verify actual changes",
                    scope.hasHardViolations() ? StageStatus.FAIL
                            : scope.hasWarnings() ? StageStatus.WARN : StageStatus.PASS,
                    0,
                    scope.hasHardViolations()
                            ? "The files changed on disk do not satisfy the scope policy."
                            : scope.hasWarnings()
                                    ? "The applied patch changed unexpected production scope."
                                    : "Observed filesystem changes match the declared patch scope.",
                    Map.of(
                            "provenance", scope.provenance(),
                            "filesChanged", scope.filesChanged(),
                            "additions", scope.additions(),
                            "deletions", scope.deletions()),
                    String.join(System.lineSeparator(), concat(
                            scope.hardViolations(), scope.warnings()))));
            warnings.addAll(scope.warnings());
            if (scope.hasHardViolations()) {
                blockingReasons.addAll(scope.hardViolations());
                return receipt(
                        verificationCase, receiptId, startedAt, startedNanos, stages,
                        blockingReasons, warnings, scope,
                        new ReproductionEvidence(
                                manifest.verifier().reproductionTest(),
                                manifest.verifier().expectedFailureType(),
                                true, baselineReproduction, null),
                        baselineRegression, null, null, mutation, workspace);
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
                    boundedTimeout(startedNanos, timeout),
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

            boolean patchedTestProcessHealthy = combinedTestProcessHealthy(
                    patchedTestRun,
                    patchedReproduction,
                    patchedRegression,
                    edgeCases);
            boolean fixed = patchedTestProcessHealthy && patchedReproduction.successful();
            record(stages, listener, stage(
                    "patched-reproduction",
                    "Verify the fix",
                    fixed ? StageStatus.PASS : StageStatus.FAIL,
                    patchedTestRun.durationMs(),
                    fixed
                            ? "The same sealed reproduction now passes in the shared patched-test run."
                            : patchedTestRun.timedOut()
                                    ? "The shared patched-test run timed out before the reproduction could be verified."
                                    : patchedReproduction.tests() == 0
                                            ? "The shared patched-test run did not execute the reproduction test."
                                            : "The patch does not fix the reproduced behaviour.",
                    sharedTestMetrics(patchedReproduction, patchedTestRun.durationMs()),
                    workspaceManager.sanitize(patchedTestRun.output(), workspace)));
            if (!fixed) {
                blockingReasons.add(patchedSuiteFailureReason(
                        patchedTestRun,
                        patchedReproduction,
                        "Reproduction test still fails after patching",
                        "The patched run did not execute the reproduction test"));
            }

            boolean regressionsPass = patchedTestProcessHealthy && patchedRegression.successful();
            record(stages, listener, stage(
                    "patched-regression",
                    "Run unchanged regressions",
                    regressionsPass ? StageStatus.PASS : StageStatus.FAIL,
                    0,
                    regressionsPass
                            ? "All original tests pass unchanged in the shared patched-test run."
                            : patchedTestRun.timedOut()
                                    ? "The shared patched-test run timed out before regressions could be verified."
                                    : patchedRegression.tests() == 0
                                            ? "The shared patched-test run did not execute the original regressions."
                                            : "The patch breaks at least one original regression.",
                    sharedTestMetrics(patchedRegression, patchedTestRun.durationMs()),
                    ""));
            if (!regressionsPass) {
                blockingReasons.add(patchedSuiteFailureReason(
                        patchedTestRun,
                        patchedRegression,
                        "Original regression suite fails after patching",
                        "The patched run did not execute the original regression suite"));
            }

            boolean edgesPass = patchedTestProcessHealthy && edgeCases.successful();
            record(stages, listener, stage(
                    "edge-cases",
                    "Generate independent edge cases",
                    edgesPass ? StageStatus.PASS : StageStatus.FAIL,
                    0,
                    edgesPass
                            ? "%d sealed dynamic edge cases pass in the shared patched-test run."
                                    .formatted(edgeCases.passed())
                            : patchedTestRun.timedOut()
                                    ? "The shared patched-test run timed out before edge cases could be verified."
                                    : edgeCases.tests() == 0
                                            ? "The shared patched-test run did not execute the independent edge cases."
                                            : "%d independent edge cases fail.".formatted(
                                                    edgeCases.failures() + edgeCases.errors()),
                    sharedTestMetrics(edgeCases, patchedTestRun.durationMs()),
                    ""));
            if (!edgesPass) {
                blockingReasons.add(patchedSuiteFailureReason(
                        patchedTestRun,
                        edgeCases,
                        "Independent edge-case suite fails",
                        "The patched run did not execute the independent edge-case suite"));
            }

            if (blockingReasons.isEmpty()) {
                ProcessResult mutationRun = mavenRunner.run(
                        patched,
                        mutationGoals(manifest.mutation()),
                        boundedTimeout(startedNanos, timeout),
                        maxLog);
                if (!mutationRun.successful()) {
                    mutation = failedMutation(manifest, mutationRun.timedOut());
                    record(stages, listener, stage(
                            "mutation",
                            "Challenge the evidence",
                            StageStatus.WARN,
                            mutationRun.durationMs(),
                            mutationRun.timedOut()
                                    ? "Mutation testing timed out; partial evidence was not accepted."
                                    : "Mutation testing exited unsuccessfully; its report was not accepted.",
                            mutationMetrics(mutation),
                            workspaceManager.sanitize(mutationRun.output(), workspace)));
                } else {
                    boolean mutationReportAvailable = true;
                    try {
                        mutation = pitestParser.parse(
                                patched,
                                scope.changedLinesByPath(),
                                manifest.mutation().minimumChangedLineScore(),
                                manifest.mutation().minimumChangedLineMutants());
                    } catch (IOException exception) {
                        mutation = unavailableReport(manifest);
                        record(stages, listener, stage(
                                "mutation",
                                "Challenge the evidence",
                                StageStatus.WARN,
                                mutationRun.durationMs(),
                                "Mutation testing completed but did not produce a parseable report.",
                                mutationMetrics(mutation),
                                workspaceManager.sanitize(mutationRun.output(), workspace)));
                        mutationReportAvailable = false;
                    }
                    if (mutationReportAvailable) {
                        boolean mutationPass = mutation.processHealthy()
                                && mutation.conclusive()
                                && mutation.changedLineMutants()
                                        >= mutation.requiredChangedLineMutants()
                                && mutation.filesWithoutMutants().isEmpty()
                                && mutation.changedLineScore() >= mutation.requiredScore();
                        StageStatus mutationStatus =
                                mutationPass ? StageStatus.PASS : StageStatus.WARN;
                        String mutationSummary;
                        if (mutationPass) {
                            mutationSummary =
                                    "Changed-line mutation score is %.1f%% across %d viable mutants."
                                            .formatted(
                                                    mutation.changedLineScore(),
                                                    mutation.changedLineMutants());
                        } else if (!mutation.processHealthy()) {
                            mutationSummary =
                                    "Mutation process health is insufficient for verification.";
                        } else if (!mutation.conclusive()) {
                            mutationSummary = "Mutation evidence is inconclusive.";
                        } else if (mutation.changedLineMutants()
                                < mutation.requiredChangedLineMutants()) {
                            mutationSummary =
                                    "Only %d viable changed-line mutants were generated; %d are required."
                                            .formatted(
                                                    mutation.changedLineMutants(),
                                                    mutation.requiredChangedLineMutants());
                        } else if (!mutation.filesWithoutMutants().isEmpty()) {
                            mutationSummary =
                                    "%d changed production files lack viable changed-line mutation evidence."
                                            .formatted(mutation.filesWithoutMutants().size());
                        } else {
                            mutationSummary = "Mutation score %.1f%% is below the %.1f%% gate."
                                    .formatted(
                                            mutation.changedLineScore(),
                                            mutation.requiredScore());
                        }
                        record(stages, listener, stage(
                                "mutation",
                                "Challenge the evidence",
                                mutationStatus,
                                mutationRun.durationMs(),
                                mutationSummary,
                                mutationMetrics(mutation),
                                workspaceManager.sanitize(mutationRun.output(), workspace)));
                    }
                }
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
                    patchedRegression, edgeCases, mutation, workspace);
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
                    baselineRegression, patchedRegression, edgeCases, mutation, workspace);
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
            MutationEvidence mutation,
            Path workspace) {
        List<String> sanitizedBlocking =
                evidenceSanitizer.strings(blockingReasons, workspace);
        List<String> sanitizedWarnings =
                evidenceSanitizer.strings(warnings, workspace);
        ScopeEvidence sanitizedScope = evidenceSanitizer.scope(scope, workspace);
        ReproductionEvidence sanitizedReproduction =
                evidenceSanitizer.reproduction(reproduction, workspace);
        TestEvidence sanitizedBaseline =
                evidenceSanitizer.tests(baselineRegression, workspace);
        TestEvidence sanitizedPatched =
                evidenceSanitizer.tests(patchedRegression, workspace);
        TestEvidence sanitizedEdges =
                evidenceSanitizer.tests(edgeCases, workspace);
        MutationEvidence sanitizedMutation =
                evidenceSanitizer.mutation(mutation, workspace);
        List<StageResult> sanitizedStages =
                evidenceSanitizer.stages(stages, workspace);

        VerdictPolicy.Decision decision =
                verdictPolicy.decide(
                        sanitizedBlocking,
                        sanitizedWarnings,
                        sanitizedMutation);

        Instant completedAt = Instant.now();
        String plainSummary = ReceiptLanguage.plainSummary(
                decision.verdict(),
                sanitizedEdges,
                sanitizedBlocking);
        List<String> limitations =
                ReceiptLanguage.limitations(sanitizedMutation, sanitizedScope);
        VerificationReceipt receipt = new VerificationReceipt(
                2,
                receiptId,
                ENGINE_VERSION,
                startedAt.toString(),
                completedAt.toString(),
                elapsed(startedNanos),
                verificationCase.manifest().caseId(),
                verificationCase.manifest().title(),
                verificationCase.candidate().patchId(),
                verificationCase.candidate().title(),
                decision.verdict(),
                decision.summary(),
                plainSummary,
                limitations,
                sanitizedBlocking,
                decision.warnings(),
                verificationCase.hashes(),
                Map.of(
                        "java", System.getProperty("java.version"),
                        "maven", "3.9.16",
                        "pitest", "1.25.4",
                        "stageTimeoutSeconds",
                                String.valueOf(effectiveStageTimeoutSeconds(
                                        verificationCase.manifest())),
                        "totalRunTimeoutSeconds", String.valueOf(totalRunTimeout.toSeconds()),
                        "operatingSystem", System.getProperty("os.name")),
                sanitizedStages,
                sanitizedReproduction,
                sanitizedBaseline,
                sanitizedPatched,
                sanitizedEdges,
                sanitizedMutation,
                sanitizedScope,
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
                        .anyMatch(failure -> failure.type().equals(expectedFailureType));
    }

    private boolean expectedReproductionObserved(TestEvidence evidence) {
        return evidence != null && evidence.tests() == 1
                && evidence.failures() == 1 && evidence.errors() == 0;
    }

    private MutationEvidence unavailableMutation(CaseManifest manifest) {
        return new MutationEvidence(
                "NOT_RUN", false, 0, 0, 0, 0, 0, 0, 0.0,
                manifest.mutation().minimumChangedLineScore(),
                manifest.mutation().minimumChangedLineMutants(),
                false, List.of(), List.of());
    }

    private MutationEvidence failedMutation(CaseManifest manifest, boolean timedOut) {
        return new MutationEvidence(
                timedOut ? "LIVE_TIMEOUT" : "LIVE_PROCESS_FAILED",
                false, 0, 0, 0, 0, 0, 0, 0.0,
                manifest.mutation().minimumChangedLineScore(),
                manifest.mutation().minimumChangedLineMutants(),
                false, List.of(), List.of());
    }

    private MutationEvidence unavailableReport(CaseManifest manifest) {
        return new MutationEvidence(
                "REPORT_NOT_AVAILABLE", true, 0, 0, 0, 0, 0, 0, 0.0,
                manifest.mutation().minimumChangedLineScore(),
                manifest.mutation().minimumChangedLineMutants(),
                false, List.of(), List.of());
    }

    private List<String> mutationGoals(CaseManifest.Mutation mutation) {
        List<String> goals = new ArrayList<>();
        goals.add("-q");
        if (!mutation.targetClasses().isEmpty()) {
            goals.add("-DtargetClasses=" + String.join(",", mutation.targetClasses()));
        }
        if (!mutation.targetTests().isEmpty()) {
            goals.add("-DtargetTests=" + String.join(",", mutation.targetTests()));
        }
        goals.add("org.pitest:pitest-maven:1.25.4:mutationCoverage");
        return List.copyOf(goals);
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
        metrics.put("requiredChangedLineMutants", evidence.requiredChangedLineMutants());
        metrics.put("processHealthy", evidence.processHealthy());
        metrics.put("filesWithoutMutants", evidence.filesWithoutMutants().size());
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

    private Duration boundedTimeout(long startedNanos, Duration stageTimeout) {
        long remainingMs = totalRunTimeout.toMillis() - elapsed(startedNanos);
        return Duration.ofMillis(Math.max(
                1,
                Math.min(stageTimeout.toMillis(), remainingMs)));
    }

    static String patchedSuiteFailureReason(
            ProcessResult process,
            TestEvidence suite,
            String failedMessage,
            String notExecutedMessage) {
        if (process.timedOut()) {
            return "Patched test run exceeded the time budget before correctness could be established";
        }
        if (suite == null || suite.tests() == 0) {
            return notExecutedMessage;
        }
        if (!suite.successful()) {
            return failedMessage;
        }
        return "Patched test process ended unsuccessfully despite complete test reports";
    }

    static boolean combinedTestProcessHealthy(
            ProcessResult process,
            TestEvidence... suites) {
        if (process == null || process.timedOut() || suites == null || suites.length == 0) {
            return false;
        }
        for (TestEvidence suite : suites) {
            if (suite == null || suite.tests() == 0) {
                return false;
            }
        }
        boolean reportedFailure = java.util.Arrays.stream(suites)
                .anyMatch(suite -> !suite.successful());
        return reportedFailure
                ? process.exitCode() != 0
                : process.exitCode() == 0;
    }

    private int effectiveStageTimeoutSeconds(CaseManifest manifest) {
        return stageTimeoutOverrideSeconds > 0
                ? stageTimeoutOverrideSeconds
                : manifest.runtime().stageTimeoutSeconds();
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
