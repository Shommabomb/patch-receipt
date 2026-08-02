package dev.patchreceipt.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoundedProcessRunnerTests {

    private final BoundedProcessRunner runner = new BoundedProcessRunner();

    @TempDir
    private Path temporaryDirectory;

    @Test
    void truncatesOutputAtTheDeclaredLimit() throws Exception {
        ProcessResult result = runner.run(
                probe("output"),
                temporaryDirectory,
                Map.of(),
                Duration.ofSeconds(10),
                128);

        assertThat(result.successful()).isTrue();
        assertThat(result.output()).hasSize(128);
        assertThat(result.outputTruncated()).isTrue();
    }

    @Test
    void timeoutTerminatesTheParentAndItsChildProcess() throws Exception {
        Path childPid = temporaryDirectory.resolve("child.pid");
        CompletableFuture<ProcessResult> runningProbe = CompletableFuture.supplyAsync(() -> {
            try {
                return runner.run(
                        probe("parent", childPid.toString()),
                        temporaryDirectory,
                        Map.of(),
                        Duration.ofSeconds(15),
                        2_000);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        });

        for (int attempt = 0;
                Files.notExists(childPid) && !runningProbe.isDone() && attempt < 200;
                attempt++) {
            Thread.sleep(50);
        }
        assertThat(childPid).exists();
        ProcessResult result = runningProbe.get(20, TimeUnit.SECONDS);

        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isEqualTo(-1);
        long pid = Long.parseLong(Files.readString(childPid).trim());

        boolean alive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        for (int attempt = 0; alive && attempt < 20; attempt++) {
            Thread.sleep(50);
            alive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        }
        assertThat(alive).isFalse();
    }

    private List<String> probe(String... arguments) {
        String executable = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe"
                : "java";
        List<String> command = new java.util.ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ProcessProbe.class.getName());
        command.addAll(List.of(arguments));
        return command;
    }
}
