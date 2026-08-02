package dev.patchreceipt.scope;

import dev.patchreceipt.casepack.CaseManifest;
import dev.patchreceipt.domain.ChangedFile;
import dev.patchreceipt.domain.ScopeEvidence;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.springframework.stereotype.Component;

@Component
public final class ObservedScopeAnalyzer {

    private static final DiffAlgorithm DIFF =
            DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM);

    public TreeSnapshot capture(Path root) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Map<String, SnapshotEntry> entries = new LinkedHashMap<>();
        try (var paths = Files.walk(normalizedRoot)) {
            for (Path path : paths.filter(candidate -> !candidate.equals(normalizedRoot)).toList()) {
                String relative = normalize(normalizedRoot.relativize(path));
                if (isVerifierMetadata(relative)) {
                    continue;
                }
                if (Files.isSymbolicLink(path)) {
                    entries.put(relative, new SnapshotEntry(
                            true,
                            Files.readSymbolicLink(path)
                                    .toString()
                                    .getBytes(StandardCharsets.UTF_8)));
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    entries.put(relative, new SnapshotEntry(false, Files.readAllBytes(path)));
                }
            }
        }
        return new TreeSnapshot(Map.copyOf(entries));
    }

    public ScopeEvidence reconcile(
            TreeSnapshot before,
            Path afterRoot,
            CaseManifest.Scope policy,
            ScopeEvidence preflight) throws IOException {
        TreeSnapshot after = capture(afterRoot);
        List<String> hardViolations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<ChangedFile> changedFiles = new ArrayList<>();

        Set<String> paths = new TreeSet<>();
        paths.addAll(before.entries().keySet());
        paths.addAll(after.entries().keySet());

        for (String path : paths) {
            SnapshotEntry oldEntry = before.entries().get(path);
            SnapshotEntry newEntry = after.entries().get(path);
            if (same(oldEntry, newEntry)) {
                continue;
            }

            MutableObservedFile observed = compare(path, oldEntry, newEntry, hardViolations);
            boolean expected = policy.expectedPaths().contains(path);
            boolean forbidden = policy.forbiddenGlobs().stream()
                    .anyMatch(glob -> ScopeAnalyzer.matchesGlob(glob, path));
            if (forbidden) {
                hardViolations.add("Forbidden path changed: " + path);
            } else if (!expected) {
                warnings.add("Unexpected path changed: " + path);
            }
            changedFiles.add(new ChangedFile(
                    path,
                    observed.additions(),
                    observed.deletions(),
                    expected,
                    forbidden,
                    observed.changedLines()));
        }

        int additions = changedFiles.stream().mapToInt(ChangedFile::additions).sum();
        int deletions = changedFiles.stream().mapToInt(ChangedFile::deletions).sum();
        Set<String> preflightPaths = preflight.files().stream()
                .map(ChangedFile::path)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        Set<String> observedPaths = changedFiles.stream()
                .map(ChangedFile::path)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        observedPaths.stream()
                .filter(path -> !preflightPaths.contains(path))
                .forEach(path -> hardViolations.add(
                        "Applied patch changed a path absent from preflight: " + path));
        preflightPaths.stream()
                .filter(path -> !observedPaths.contains(path))
                .forEach(path -> hardViolations.add(
                        "Preflight reported a path that did not change: " + path));
        if (changedFiles.size() > policy.maximumFiles()) {
            hardViolations.add("Applied patch changes %d files; maximum is %d"
                    .formatted(changedFiles.size(), policy.maximumFiles()));
        }
        if (additions + deletions > policy.maximumChangedLines()) {
            hardViolations.add("Applied patch changes %d lines; maximum is %d"
                    .formatted(additions + deletions, policy.maximumChangedLines()));
        }

        return new ScopeEvidence(
                "OBSERVED_FILESYSTEM",
                changedFiles.size(),
                additions,
                deletions,
                changedFiles,
                distinct(hardViolations),
                distinct(warnings));
    }

    private MutableObservedFile compare(
            String path,
            SnapshotEntry oldEntry,
            SnapshotEntry newEntry,
            List<String> hardViolations) {
        if ((oldEntry != null && oldEntry.symbolicLink())
                || (newEntry != null && newEntry.symbolicLink())) {
            hardViolations.add("Symbolic-link changes are not allowed: " + path);
            return new MutableObservedFile(0, 0, Set.of());
        }

        byte[] oldBytes = oldEntry == null ? new byte[0] : oldEntry.content();
        byte[] newBytes = newEntry == null ? new byte[0] : newEntry.content();
        if (RawText.isBinary(oldBytes) || RawText.isBinary(newBytes)) {
            hardViolations.add("Binary file changed after patch application: " + path);
            return new MutableObservedFile(0, 0, Set.of());
        }

        RawText oldText = new RawText(oldBytes);
        RawText newText = new RawText(newBytes);
        int additions = 0;
        int deletions = 0;
        Set<Integer> changedLines = new LinkedHashSet<>();
        for (Edit edit : DIFF.diff(RawTextComparator.DEFAULT, oldText, newText)) {
            additions += edit.getLengthB();
            deletions += edit.getLengthA();
            for (int line = edit.getBeginB() + 1; line <= edit.getEndB(); line++) {
                changedLines.add(line);
            }
        }
        return new MutableObservedFile(additions, deletions, changedLines);
    }

    private boolean same(SnapshotEntry first, SnapshotEntry second) {
        return first == null && second == null
                || first != null
                        && second != null
                        && first.symbolicLink() == second.symbolicLink()
                        && Arrays.equals(first.content(), second.content());
    }

    private String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private boolean isVerifierMetadata(String path) {
        return ".git".equals(path) || path.startsWith(".git/");
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    public record TreeSnapshot(Map<String, SnapshotEntry> entries) {

        public TreeSnapshot {
            entries = entries == null ? Map.of() : Map.copyOf(entries);
        }
    }

    public record SnapshotEntry(boolean symbolicLink, byte[] content) {

        public SnapshotEntry {
            content = content == null ? new byte[0] : content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    private record MutableObservedFile(
            int additions,
            int deletions,
            Set<Integer> changedLines) {

        private MutableObservedFile {
            changedLines = Set.copyOf(changedLines);
        }
    }
}
