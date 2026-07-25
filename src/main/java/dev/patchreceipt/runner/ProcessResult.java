package dev.patchreceipt.runner;

public record ProcessResult(
        int exitCode,
        boolean timedOut,
        long durationMs,
        String output,
        boolean outputTruncated) {

    public boolean successful() {
        return !timedOut && exitCode == 0;
    }
}
