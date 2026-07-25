package dev.patchreceipt.runner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public final class BoundedProcessRunner {

    public ProcessResult run(
            List<String> command,
            Path workingDirectory,
            Map<String, String> environment,
            Duration timeout,
            int maximumCharacters) throws IOException, InterruptedException {
        long started = System.nanoTime();
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();

        StringBuilder output = new StringBuilder(Math.min(maximumCharacters, 8192));
        AtomicBoolean truncated = new AtomicBoolean(false);
        Thread reader = Thread.ofVirtual().name("patchreceipt-process-output").start(() -> {
            try (BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    synchronized (output) {
                        int remaining = maximumCharacters - output.length();
                        if (remaining > 0) {
                            String value = line + System.lineSeparator();
                            output.append(value, 0, Math.min(value.length(), remaining));
                        }
                        if (remaining <= line.length()) {
                            truncated.set(true);
                        }
                    }
                }
            } catch (IOException exception) {
                synchronized (output) {
                    if (output.length() < maximumCharacters) {
                        output.append("[output reader failed: ")
                                .append(exception.getMessage())
                                .append(']');
                    }
                }
            }
        });

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            terminateTree(process);
        }
        reader.join(Duration.ofSeconds(3));

        int exitCode = finished ? process.exitValue() : -1;
        return new ProcessResult(
                exitCode,
                !finished,
                Duration.ofNanos(System.nanoTime() - started).toMillis(),
                output.toString(),
                truncated.get());
    }

    private void terminateTree(Process process) {
        process.descendants().forEach(handle -> {
            handle.destroy();
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        });
        process.destroy();
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
