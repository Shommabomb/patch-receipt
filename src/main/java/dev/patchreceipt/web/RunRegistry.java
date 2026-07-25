package dev.patchreceipt.web;

import dev.patchreceipt.casepack.BundledCaseRepository;
import dev.patchreceipt.casepack.VerificationCase;
import dev.patchreceipt.domain.StageResult;
import dev.patchreceipt.domain.VerificationReceipt;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public final class RunRegistry {

    private static final Duration RETENTION = Duration.ofMinutes(30);

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
            executor.execute(() -> execute(job, verificationCase));
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
        RunJob job = requireJob(runId);
        VerificationReceipt receipt = job.receipt;
        if (receipt == null) {
            throw new IllegalStateException("Receipt is not ready");
        }
        return receipt;
    }

    private void execute(RunJob job, VerificationCase verificationCase) {
        job.state = RunState.RUNNING;
        job.currentStage = "Starting isolated verification";
        VerificationReceipt receipt = engine.verify(
                verificationCase,
                stage -> {
                    job.stages.add(stage);
                    job.currentStage = stage.title();
                });
        job.receipt = receipt;
        job.completedAt = Instant.now();
        job.currentStage = "Receipt complete";
        job.state = RunState.COMPLETED;
    }

    private RunJob requireJob(String runId) {
        RunJob job = jobs.get(runId);
        if (job == null) {
            throw new NoSuchElementException("Unknown or expired run: " + runId);
        }
        return job;
    }

    private RunSnapshot snapshot(RunJob job) {
        VerificationReceipt receipt = job.receipt;
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
                job.state,
                job.currentStage,
                job.createdAt.toString(),
                job.completedAt == null ? null : job.completedAt.toString(),
                receipt == null ? null : receipt.verdict(),
                receipt == null ? null : receipt.verdictSummary(),
                stageViews,
                links);
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minus(RETENTION);
        jobs.entrySet().removeIf(entry -> {
            RunJob job = entry.getValue();
            Instant reference = job.completedAt == null ? job.createdAt : job.completedAt;
            return reference.isBefore(cutoff) && job.state == RunState.COMPLETED;
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

        private RunJob(String runId, String caseId, String patchId, Instant createdAt) {
            this.runId = runId;
            this.caseId = caseId;
            this.patchId = patchId;
            this.createdAt = createdAt;
        }
    }
}
