package dev.patchreceipt.web;

import dev.patchreceipt.casepack.BundledCaseRepository;
import dev.patchreceipt.casepack.VerificationCase;
import dev.patchreceipt.domain.StageResult;
import dev.patchreceipt.domain.TestEvidence;
import dev.patchreceipt.domain.TestFailure;
import dev.patchreceipt.domain.VerificationReceipt;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class RunRegistry {

    private static final Duration RETENTION = Duration.ofMinutes(30);
    private static final Logger LOGGER = LoggerFactory.getLogger(RunRegistry.class);

    private final BundledCaseRepository cases;
    private final dev.patchreceipt.engine.VerificationEngine engine;
    private final Map<String, RunJob> jobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(3),
            Thread.ofVirtual().name("patchreceipt-run-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    public RunRegistry(
            BundledCaseRepository cases,
            dev.patchreceipt.engine.VerificationEngine engine) {
        this.cases = cases;
        this.engine = engine;
    }

    public RunSnapshot start(String caseId, String patchId) {
        purgeExpired();
        VerificationCase verificationCase = cases.loadHosted(caseId, patchId);
        RunJob job = new RunJob(
                UUID.randomUUID().toString(),
                caseId,
                patchId,
                Instant.now());
        jobs.put(job.runId, job);
        try {
            job.task = executor.submit(() -> execute(job, verificationCase));
        } catch (RejectedExecutionException exception) {
            jobs.remove(job.runId);
            throw new QueueFullException();
        }
        return snapshot(job);
    }

    public RunSnapshot find(String runId) {
        purgeExpired();
        return snapshot(requireJob(runId));
    }

    public VerificationReceipt receipt(String runId) {
        purgeExpired();
        RunJob job = requireJob(runId);
        VerificationReceipt receipt = job.receipt;
        if (receipt == null) {
            throw new IllegalStateException("Receipt is not ready");
        }
        return receipt;
    }

    private void execute(RunJob job, VerificationCase verificationCase) {
        try {
            job.state = RunState.RUNNING;
            job.currentStage = "Starting isolated verification";
            VerificationReceipt receipt = engine.verify(
                    verificationCase,
                    stage -> {
                        job.stages.add(stage);
                        job.currentStage = stage.title();
                    });
            if (receipt == null) {
                throw new IllegalStateException("Verification engine returned no receipt");
            }
            job.receipt = receipt;
            job.completedAt = Instant.now();
            job.currentStage = "Receipt complete";
            job.state = RunState.COMPLETED;
        } catch (Throwable failure) {
            LOGGER.error("Verification worker failed for run {}", job.runId, failure);
            job.failureMessage =
                    "The verification worker stopped unexpectedly. Please run the candidate again.";
            job.completedAt = Instant.now();
            job.currentStage = "Verification failed";
            job.state = RunState.FAILED;
        }
    }

    private RunJob requireJob(String runId) {
        RunJob job = jobs.get(runId);
        if (job == null) {
            throw new NoSuchElementException("Unknown or expired run: " + runId);
        }
        return job;
    }

    private RunSnapshot snapshot(RunJob job) {
        RunState state = job.state;
        VerificationReceipt receipt = job.receipt;
        state = stableState(state, receipt);
        List<RunSnapshot.StageProgress> stageViews;
        synchronized (job.stages) {
            stageViews = job.stages.stream()
                    .map(stage -> new RunSnapshot.StageProgress(
                            stage.id(),
                            stage.title(),
                            stage.status(),
                            stage.durationMs(),
                            stage.summary()))
                    .toList();
        }
        RunSnapshot.ReceiptLinks links = receipt == null
                ? null
                : new RunSnapshot.ReceiptLinks(
                        "/api/v1/runs/" + job.runId + "/receipt.json",
                        "/api/v1/runs/" + job.runId + "/receipt.md",
                        "/api/v1/runs/" + job.runId + "/receipt.html");
        return new RunSnapshot(
                job.runId,
                job.caseId,
                job.patchId,
                state,
                job.currentStage,
                job.createdAt.toString(),
                job.completedAt == null ? null : job.completedAt.toString(),
                receipt == null ? null : receipt.verdict(),
                receipt == null ? null : receipt.verdictSummary(),
                receipt == null ? null : receipt.plainSummary(),
                job.failureMessage,
                receipt == null ? List.of() : receipt.limitations(),
                stageViews,
                summarizeEvidence(receipt),
                links);
    }

    static RunSnapshot.EvidenceSummary summarizeEvidence(VerificationReceipt receipt) {
        if (receipt == null) {
            return null;
        }

        List<RunSnapshot.Metric> metrics = List.of(
                new RunSnapshot.Metric(
                        "Regressions",
                        ratio(receipt.patchedRegression()),
                        "Did existing behaviour still work?"),
                new RunSnapshot.Metric(
                        "Edge cases",
                        ratio(receipt.edgeCases()),
                        "Did the extra checks pass?"),
                new RunSnapshot.Metric(
                        "Mutation",
                        mutationScore(receipt),
                        "Did tests catch deliberate faults?"),
                new RunSnapshot.Metric(
                        "Scope",
                        scopeStatus(receipt),
                        "Did only expected files change?"));

        List<String> findings = new ArrayList<>();
        if (receipt.edgeCases() != null) {
            List<TestFailure> failures = receipt.edgeCases().failureDetails();
            failures.stream()
                    .limit(3)
                    .map(RunRegistry::edgeCaseFinding)
                    .forEach(findings::add);
            if (failures.size() > 3) {
                findings.add("%d additional edge-case failures are in the full receipt."
                        .formatted(failures.size() - 3));
            }
        }
        receipt.blockingReasons().stream()
                .filter(value -> !findings.contains(value))
                .forEach(findings::add);
        receipt.warnings().stream()
                .filter(value -> !findings.contains(value))
                .forEach(findings::add);
        if (findings.isEmpty()) {
            findings.add("No blocking findings or scope warnings.");
        }

        return new RunSnapshot.EvidenceSummary(metrics, findings);
    }

    private static String ratio(TestEvidence evidence) {
        if (evidence == null || evidence.tests() == 0) {
            return "Not run";
        }
        return "%d / %d".formatted(evidence.passed(), evidence.tests());
    }

    private static String mutationScore(VerificationReceipt receipt) {
        if (receipt.mutation() == null
                || !receipt.mutation().processHealthy()
                || !receipt.mutation().conclusive()
                || !receipt.mutation().filesWithoutMutants().isEmpty()) {
            return "Inconclusive";
        }
        if (receipt.mutation().changedLineMutants()
                < receipt.mutation().requiredChangedLineMutants()) {
            return "Insufficient";
        }
        return String.format(
                Locale.ROOT,
                "%.0f%% · %d %s",
                receipt.mutation().changedLineScore(),
                receipt.mutation().changedLineMutants(),
                receipt.mutation().changedLineMutants() == 1 ? "mutant" : "mutants");
    }

    private static String scopeStatus(VerificationReceipt receipt) {
        if (receipt.scope() == null) {
            return "Unavailable";
        }
        if (receipt.scope().hasHardViolations()) {
            return "Blocked";
        }
        long unexpected = receipt.scope().files().stream()
                .filter(file -> !file.expected() && !file.forbidden())
                .count();
        if (unexpected == 0) {
            return "Clean";
        }
        return "%d unexpected %s".formatted(
                unexpected,
                unexpected == 1 ? "file" : "files");
    }

    private static String edgeCaseFinding(TestFailure failure) {
        boolean hasMessage = failure.message() != null && !failure.message().isBlank();
        String message = !hasMessage
                ? failure.type()
                : failure.message();
        String compactMessage = message == null
                ? "Failed"
                : message.replaceAll("\\s+", " ")
                        .replace(" ==> ", " — ")
                        .trim();
        if (compactMessage.length() > 180) {
            compactMessage = compactMessage.substring(0, 177) + "...";
        }
        if (failure.testName().startsWith("generatedContractCases()[") && hasMessage) {
            return compactMessage;
        }
        return "%s: %s".formatted(failure.testName(), compactMessage);
    }

    static RunState stableState(RunState state, VerificationReceipt receipt) {
        return state == RunState.COMPLETED && receipt == null
                ? RunState.RUNNING
                : state;
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minus(RETENTION);
        jobs.entrySet().removeIf(entry -> {
            RunJob job = entry.getValue();
            Instant reference = job.completedAt == null ? job.createdAt : job.completedAt;
            if (!reference.isBefore(cutoff)) {
                return false;
            }
            Future<?> task = job.task;
            if (task != null && !task.isDone()) {
                task.cancel(true);
            }
            return true;
        });
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }

    private static final class RunJob {

        private final String runId;
        private final String caseId;
        private final String patchId;
        private final Instant createdAt;
        private final List<StageResult> stages =
                java.util.Collections.synchronizedList(new ArrayList<>());
        private volatile RunState state = RunState.QUEUED;
        private volatile String currentStage = "Waiting for the verifier";
        private volatile Instant completedAt;
        private volatile VerificationReceipt receipt;
        private volatile String failureMessage;
        private volatile Future<?> task;

        private RunJob(String runId, String caseId, String patchId, Instant createdAt) {
            this.runId = runId;
            this.caseId = caseId;
            this.patchId = patchId;
            this.createdAt = createdAt;
        }
    }
}
