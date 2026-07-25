package dev.patchreceipt.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record ScopeEvidence(
        int filesChanged,
        int additions,
        int deletions,
        List<ChangedFile> files,
        List<String> hardViolations,
        List<String> warnings) {

    public ScopeEvidence {
        files = files == null ? List.of() : List.copyOf(files);
        hardViolations = hardViolations == null ? List.of() : List.copyOf(hardViolations);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean hasHardViolations() {
        return !hardViolations.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public Map<String, Set<Integer>> changedLinesByPath() {
        return files.stream().collect(Collectors.toUnmodifiableMap(
                ChangedFile::path,
                ChangedFile::changedLines));
    }
}
