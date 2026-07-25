package dev.patchreceipt.domain;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public record ChangedFile(
        String path,
        int additions,
        int deletions,
        boolean expected,
        boolean forbidden,
        Set<Integer> changedLines) {

    public ChangedFile {
        changedLines = changedLines == null
                ? Set.of()
                : Collections.unmodifiableSet(new TreeSet<>(changedLines));
    }
}
