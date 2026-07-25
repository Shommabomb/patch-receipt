package dev.patchreceipt.runner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ProcessProbe {

    private ProcessProbe() {
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "output" -> System.out.print("x".repeat(10_000));
            case "child" -> Thread.sleep(60_000);
            case "parent" -> {
                Process child = new ProcessBuilder(
                        javaExecutable(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        ProcessProbe.class.getName(),
                        "child")
                        .start();
                Files.writeString(
                        Path.of(args[1]),
                        Long.toString(child.pid()),
                        StandardCharsets.UTF_8);
                Thread.sleep(60_000);
            }
            default -> throw new IllegalArgumentException("Unknown probe mode");
        }
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }
}
