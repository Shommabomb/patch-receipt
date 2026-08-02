package dev.patchreceipt.receipt;

import dev.patchreceipt.domain.ChangedFile;
import dev.patchreceipt.domain.MutationEvidence;
import dev.patchreceipt.domain.MutationFinding;
import dev.patchreceipt.domain.ReproductionEvidence;
import dev.patchreceipt.domain.ScopeEvidence;
import dev.patchreceipt.domain.StageResult;
import dev.patchreceipt.domain.TestEvidence;
import dev.patchreceipt.domain.TestFailure;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class EvidenceSanitizer {

    private final List<Mask> fixedMasks;

    public EvidenceSanitizer(
            @Value("${patchreceipt.application-root:${user.dir}}") String applicationRoot,
            @Value("${patchreceipt.maven-user-home:${user.dir}/.cache/maven}") String mavenUserHome,
            @Value("${user.home}") String userHome) {
        this.fixedMasks = List.of(
                        mask(applicationRoot, "<application-root>"),
                        mask(mavenUserHome, "<maven-home>"),
                        mask(userHome, "<user-home>"))
                .stream()
                .sorted(Comparator.comparingInt((Mask mask) -> mask.path().length()).reversed())
                .toList();
    }

    public String text(String value, Path workspace) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        String sanitized = value;
        List<Mask> masks = new ArrayList<>(fixedMasks);
        if (workspace != null) {
            masks.add(mask(workspace.toAbsolutePath().normalize().toString(), "<workspace>"));
        }
        masks.sort(Comparator.comparingInt((Mask mask) -> mask.path().length()).reversed());
        for (Mask mask : masks) {
            for (String variant : pathVariants(mask.path())) {
                sanitized = replaceIgnoreCase(sanitized, variant, mask.label());
            }
        }
        return sanitized;
    }

    public List<String> strings(List<String> values, Path workspace) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(value -> text(value, workspace)).toList();
    }

    public List<StageResult> stages(List<StageResult> stages, Path workspace) {
        if (stages == null) {
            return List.of();
        }
        return stages.stream()
                .map(stage -> new StageResult(
                        text(stage.id(), workspace),
                        text(stage.title(), workspace),
                        stage.status(),
                        stage.durationMs(),
                        text(stage.summary(), workspace),
                        metrics(stage.metrics(), workspace),
                        text(stage.log(), workspace)))
                .toList();
    }

    public ReproductionEvidence reproduction(
            ReproductionEvidence evidence,
            Path workspace) {
        if (evidence == null) {
            return null;
        }
        return new ReproductionEvidence(
                text(evidence.testClass(), workspace),
                text(evidence.expectedFailureType(), workspace),
                evidence.expectedBaselineFailureObserved(),
                tests(evidence.baseline(), workspace),
                tests(evidence.patched(), workspace));
    }

    public TestEvidence tests(TestEvidence evidence, Path workspace) {
        if (evidence == null) {
            return null;
        }
        List<TestFailure> failures = evidence.failureDetails().stream()
                .map(failure -> new TestFailure(
                        text(failure.testClass(), workspace),
                        text(failure.testName(), workspace),
                        text(failure.type(), workspace),
                        text(failure.message(), workspace)))
                .toList();
        return new TestEvidence(
                evidence.tests(),
                evidence.passed(),
                evidence.failures(),
                evidence.errors(),
                evidence.skipped(),
                evidence.durationMs(),
                failures);
    }

    public MutationEvidence mutation(MutationEvidence evidence, Path workspace) {
        if (evidence == null) {
            return null;
        }
        List<MutationFinding> findings = evidence.survivingMutants().stream()
                .map(finding -> new MutationFinding(
                        text(finding.status(), workspace),
                        text(finding.mutatedClass(), workspace),
                        text(finding.mutatedMethod(), workspace),
                        finding.lineNumber(),
                        text(finding.mutator(), workspace),
                        text(finding.description(), workspace),
                        text(finding.killingTest(), workspace)))
                .toList();
        return new MutationEvidence(
                text(evidence.provenance(), workspace),
                evidence.processHealthy(),
                evidence.totalMutants(),
                evidence.changedLineMutants(),
                evidence.killed(),
                evidence.survived(),
                evidence.uncovered(),
                evidence.timedOutOrErrored(),
                evidence.changedLineScore(),
                evidence.requiredScore(),
                evidence.requiredChangedLineMutants(),
                evidence.conclusive(),
                strings(evidence.filesWithoutMutants(), workspace),
                findings);
    }

    public ScopeEvidence scope(ScopeEvidence evidence, Path workspace) {
        if (evidence == null) {
            return null;
        }
        List<ChangedFile> files = evidence.files().stream()
                .map(file -> new ChangedFile(
                        text(file.path(), workspace),
                        file.additions(),
                        file.deletions(),
                        file.expected(),
                        file.forbidden(),
                        file.changedLines()))
                .toList();
        return new ScopeEvidence(
                text(evidence.provenance(), workspace),
                evidence.filesChanged(),
                evidence.additions(),
                evidence.deletions(),
                files,
                strings(evidence.hardViolations(), workspace),
                strings(evidence.warnings(), workspace));
    }

    private Map<String, Object> metrics(Map<String, Object> metrics, Path workspace) {
        if (metrics == null || metrics.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        metrics.forEach((key, value) -> sanitized.put(
                text(key, workspace),
                metricValue(value, workspace)));
        return Map.copyOf(sanitized);
    }

    private Object metricValue(Object value, Path workspace) {
        if (value instanceof CharSequence sequence) {
            return text(sequence.toString(), workspace);
        }
        if (value instanceof Path path) {
            return text(path.toString(), workspace);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, nested) -> sanitized.put(
                    text(String.valueOf(key), workspace),
                    metricValue(nested, workspace)));
            return Map.copyOf(sanitized);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            iterable.forEach(nested -> sanitized.add(metricValue(nested, workspace)));
            return List.copyOf(sanitized);
        }
        return value;
    }

    private static String replaceIgnoreCase(
            String value,
            String target,
            String replacement) {
        if (target == null || target.isBlank()) {
            return value;
        }
        return Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE)
                .matcher(value)
                .replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static Mask mask(String path, String label) {
        return new Mask(Path.of(path).toAbsolutePath().normalize().toString(), label);
    }

    private static Set<String> pathVariants(String value) {
        Set<String> variants = new LinkedHashSet<>();
        String slashPath = value.replace('\\', '/');
        variants.add(value);
        variants.add(slashPath);
        variants.add(value.replace(" ", "%20"));
        variants.add(slashPath.replace(" ", "%20"));

        String rawPath = Path.of(value).toUri().getRawPath();
        if (rawPath != null && !rawPath.isBlank()) {
            variants.add(rawPath);
            if (rawPath.matches("^/[A-Za-z]:/.*")) {
                variants.add(rawPath.substring(1));
            }
        }
        return variants;
    }

    private record Mask(String path, String label) {
    }
}
