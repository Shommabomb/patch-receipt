package dev.patchreceipt.scope;

import dev.patchreceipt.casepack.CaseManifest;
import dev.patchreceipt.domain.ChangedFile;
import dev.patchreceipt.domain.ScopeEvidence;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class ScopeAnalyzer {

    private static final Pattern DIFF_HEADER =
            Pattern.compile("^diff --git a/(.+) b/(.+)$");
    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@.*$");

    public ScopeEvidence analyze(String patch, CaseManifest.Scope policy) {
        List<String> hardViolations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, MutableChangedFile> files = new LinkedHashMap<>();

        if (patch == null || patch.isBlank()) {
            hardViolations.add("Patch is empty");
            return new ScopeEvidence(0, 0, 0, List.of(), hardViolations, warnings);
        }
        if (patch.indexOf('\0') >= 0 || patch.contains("GIT binary patch")
                || patch.contains("Binary files ")) {
            hardViolations.add("Binary patches are not allowed");
        }

        MutableChangedFile current = null;
        boolean inHunk = false;
        int newLine = 0;

        for (String line : patch.split("\\R", -1)) {
            Matcher diffMatcher = DIFF_HEADER.matcher(line);
            if (diffMatcher.matches()) {
                String oldPath = normalize(diffMatcher.group(1));
                String newPath = normalize(diffMatcher.group(2));
                if (escapesRoot(oldPath) || escapesRoot(newPath)) {
                    hardViolations.add("Patch path escapes project root: " + newPath);
                }
                current = files.computeIfAbsent(newPath, MutableChangedFile::new);
                inHunk = false;
                continue;
            }

            Matcher hunkMatcher = HUNK_HEADER.matcher(line);
            if (hunkMatcher.matches()) {
                if (current == null) {
                    hardViolations.add("Hunk appears before a file header");
                    continue;
                }
                newLine = Integer.parseInt(hunkMatcher.group(1));
                inHunk = true;
                continue;
            }

            if (!inHunk || current == null) {
                continue;
            }
            if (line.startsWith("+") && !line.startsWith("+++")) {
                current.additions++;
                current.changedLines.add(newLine);
                newLine++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                current.deletions++;
            } else if (line.startsWith(" ")) {
                newLine++;
            } else if (line.startsWith("\\ No newline")) {
                // Metadata; no line movement.
            } else {
                inHunk = false;
            }
        }

        if (files.isEmpty()) {
            hardViolations.add("Patch contains no unified diff file headers");
        }

        int additions = 0;
        int deletions = 0;
        List<ChangedFile> changedFiles = new ArrayList<>();
        for (MutableChangedFile file : files.values()) {
            additions += file.additions;
            deletions += file.deletions;
            boolean expected = policy.expectedPaths().contains(file.path);
            boolean forbidden = policy.forbiddenGlobs().stream()
                    .anyMatch(glob -> matchesGlob(glob, file.path));
            if (forbidden) {
                hardViolations.add("Forbidden path changed: " + file.path);
            } else if (!expected) {
                warnings.add("Unexpected path changed: " + file.path);
            }
            changedFiles.add(new ChangedFile(
                    file.path, file.additions, file.deletions, expected, forbidden, file.changedLines));
        }

        if (files.size() > policy.maximumFiles()) {
            hardViolations.add("Patch changes %d files; maximum is %d"
                    .formatted(files.size(), policy.maximumFiles()));
        }
        if (additions + deletions > policy.maximumChangedLines()) {
            hardViolations.add("Patch changes %d lines; maximum is %d"
                    .formatted(additions + deletions, policy.maximumChangedLines()));
        }

        return new ScopeEvidence(
                files.size(), additions, deletions, changedFiles,
                distinct(hardViolations), distinct(warnings));
    }

    static boolean matchesGlob(String glob, String path) {
        String normalizedGlob = normalize(glob);
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < normalizedGlob.length(); index++) {
            char character = normalizedGlob.charAt(index);
            if (character == '*') {
                boolean doubleStar = index + 1 < normalizedGlob.length()
                        && normalizedGlob.charAt(index + 1) == '*';
                regex.append(doubleStar ? ".*" : "[^/]*");
                if (doubleStar) {
                    index++;
                }
            } else if (character == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(character) >= 0) {
                regex.append('\\').append(character);
            } else {
                regex.append(character);
            }
        }
        return path.matches(regex.append('$').toString());
    }

    private static String normalize(String path) {
        return path.replace('\\', '/')
                .replaceFirst("^\\./", "");
    }

    private static boolean escapesRoot(String path) {
        return path.startsWith("/")
                || path.matches("^[A-Za-z]:.*")
                || List.of(path.split("/")).contains("..");
    }

    private static List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private static final class MutableChangedFile {
        private final String path;
        private int additions;
        private int deletions;
        private final Set<Integer> changedLines = new LinkedHashSet<>();

        private MutableChangedFile(String path) {
            this.path = path;
        }
    }
}
