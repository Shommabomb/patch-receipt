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
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");
    private static final Pattern OLD_FILE_HEADER =
            Pattern.compile("^--- (?:a/(.+)|/dev/null)(?:\\t.*)?$");
    private static final Pattern NEW_FILE_HEADER =
            Pattern.compile("^\\+\\+\\+ (?:b/(.+)|/dev/null)(?:\\t.*)?$");

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
        int expectedOldLines = 0;
        int expectedNewLines = 0;
        int observedOldLines = 0;
        int observedNewLines = 0;

        for (String line : patchLines(patch)) {
            Matcher diffMatcher = DIFF_HEADER.matcher(line);
            if (diffMatcher.matches()) {
                validateHunk(
                        current,
                        inHunk,
                        expectedOldLines,
                        expectedNewLines,
                        observedOldLines,
                        observedNewLines,
                        hardViolations);
                validateFileHeaders(current, hardViolations);
                String oldPath = normalize(diffMatcher.group(1));
                String newPath = normalize(diffMatcher.group(2));
                boolean invalidPath = escapesRoot(oldPath) || escapesRoot(newPath);
                if (invalidPath) {
                    hardViolations.add("Patch path escapes project root: " + newPath);
                }
                if (containsVerifierMetadata(oldPath) || containsVerifierMetadata(newPath)) {
                    hardViolations.add("Verifier metadata path changed: " + newPath);
                }
                if (!invalidPath && !oldPath.equals(newPath)) {
                    hardViolations.add(
                            "File renames are not supported: %s -> %s"
                                    .formatted(oldPath, newPath));
                }
                current = files.computeIfAbsent(
                        newPath,
                        ignored -> new MutableChangedFile(oldPath, newPath));
                inHunk = false;
                continue;
            }

            if (!inHunk && current != null) {
                Matcher oldHeader = OLD_FILE_HEADER.matcher(line);
                if (oldHeader.matches()) {
                    current.oldHeaders++;
                    String headerPath = oldHeader.group(1);
                    if (headerPath != null
                            && !normalize(headerPath).equals(current.oldPath)) {
                        hardViolations.add(
                                "Old file header does not match diff header: " + headerPath);
                    }
                    continue;
                }
                Matcher newHeader = NEW_FILE_HEADER.matcher(line);
                if (newHeader.matches()) {
                    current.newHeaders++;
                    String headerPath = newHeader.group(1);
                    if (headerPath != null
                            && !normalize(headerPath).equals(current.path)) {
                        hardViolations.add(
                                "New file header does not match diff header: " + headerPath);
                    }
                    continue;
                }
            }

            Matcher hunkMatcher = HUNK_HEADER.matcher(line);
            if (hunkMatcher.matches()) {
                validateHunk(
                        current,
                        inHunk,
                        expectedOldLines,
                        expectedNewLines,
                        observedOldLines,
                        observedNewLines,
                        hardViolations);
                if (current == null) {
                    hardViolations.add("Hunk appears before a file header");
                    continue;
                }
                if (current.oldHeaders != 1 || current.newHeaders != 1) {
                    hardViolations.add(
                            "Hunk appears before complete file headers: " + current.path);
                }
                expectedOldLines = count(hunkMatcher.group(2));
                expectedNewLines = count(hunkMatcher.group(4));
                observedOldLines = 0;
                observedNewLines = 0;
                newLine = Integer.parseInt(hunkMatcher.group(3));
                current.hunks++;
                inHunk = true;
                continue;
            }

            if (!inHunk || current == null) {
                continue;
            }
            if (line.startsWith("+")) {
                current.additions++;
                current.changedLines.add(newLine);
                newLine++;
                observedNewLines++;
            } else if (line.startsWith("-")) {
                current.deletions++;
                observedOldLines++;
            } else if (line.startsWith(" ") || line.isEmpty()) {
                newLine++;
                observedOldLines++;
                observedNewLines++;
            } else if (line.startsWith("\\ No newline")) {
                // Metadata; no line movement.
            } else {
                hardViolations.add("Unparseable line in hunk for " + current.path);
                inHunk = false;
            }
        }
        validateHunk(
                current,
                inHunk,
                expectedOldLines,
                expectedNewLines,
                observedOldLines,
                observedNewLines,
                hardViolations);
        validateFileHeaders(current, hardViolations);

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
            if (containsVerifierMetadata(file.path)) {
                forbidden = true;
                hardViolations.add("Verifier metadata path changed: " + file.path);
            } else if (forbidden) {
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
                "PATCH_PREFLIGHT",
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

    private static boolean containsVerifierMetadata(String path) {
        return List.of(normalize(path).split("/")).contains(".git");
    }

    private static List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private static List<String> patchLines(String patch) {
        List<String> lines = new ArrayList<>(List.of(patch.split("\\R", -1)));
        if (!lines.isEmpty()
                && lines.getLast().isEmpty()
                && (patch.endsWith("\n") || patch.endsWith("\r"))) {
            lines.removeLast();
        }
        return lines;
    }

    private static int count(String value) {
        return value == null ? 1 : Integer.parseInt(value);
    }

    private static void validateHunk(
            MutableChangedFile current,
            boolean inHunk,
            int expectedOldLines,
            int expectedNewLines,
            int observedOldLines,
            int observedNewLines,
            List<String> hardViolations) {
        if (!inHunk || current == null) {
            return;
        }
        if (expectedOldLines != observedOldLines || expectedNewLines != observedNewLines) {
            hardViolations.add(
                    "Hunk line counts do not match header for %s: expected -%d/+%d but observed -%d/+%d"
                            .formatted(
                                    current.path,
                                    expectedOldLines,
                                    expectedNewLines,
                                    observedOldLines,
                                    observedNewLines));
        }
    }

    private static void validateFileHeaders(
            MutableChangedFile current,
            List<String> hardViolations) {
        if (current == null) {
            return;
        }
        if (current.oldHeaders != 1 || current.newHeaders != 1) {
            hardViolations.add(
                    "Diff file must contain exactly one --- and one +++ header: " + current.path);
        }
        if (current.hunks == 0) {
            hardViolations.add("Diff file contains no hunks: " + current.path);
        }
    }

    private static final class MutableChangedFile {
        private final String oldPath;
        private final String path;
        private int additions;
        private int deletions;
        private int oldHeaders;
        private int newHeaders;
        private int hunks;
        private final Set<Integer> changedLines = new LinkedHashSet<>();

        private MutableChangedFile(String oldPath, String path) {
            this.oldPath = oldPath;
            this.path = path;
        }
    }
}
