package dev.patchreceipt.cli;

import dev.patchreceipt.casepack.LocalCaseLoader;
import dev.patchreceipt.domain.Verdict;
import dev.patchreceipt.engine.VerificationEngine;
import dev.patchreceipt.receipt.HtmlReceiptRenderer;
import dev.patchreceipt.receipt.JsonReceiptRenderer;
import dev.patchreceipt.receipt.MarkdownReceiptRenderer;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
public final class PatchReceiptCli {

    private final LocalCaseLoader localCases;
    private final VerificationEngine engine;
    private final JsonReceiptRenderer json;
    private final MarkdownReceiptRenderer markdown;
    private final HtmlReceiptRenderer html;

    public PatchReceiptCli(
            LocalCaseLoader localCases,
            VerificationEngine engine,
            JsonReceiptRenderer json,
            MarkdownReceiptRenderer markdown,
            HtmlReceiptRenderer html) {
        this.localCases = localCases;
        this.engine = engine;
        this.json = json;
        this.markdown = markdown;
        this.html = html;
    }

    public int execute(String[] args) {
        RootCommand root = new RootCommand();
        CommandLine commandLine = new CommandLine(root);
        commandLine.addSubcommand("verify", new VerifyCommand());
        commandLine.addSubcommand("init", new InitCommand());
        commandLine.setOut(new PrintWriter(System.out, true, StandardCharsets.UTF_8));
        commandLine.setErr(new PrintWriter(System.err, true, StandardCharsets.UTF_8));
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        return commandLine.execute(args);
    }

    @CommandLine.Command(
            name = "patchreceipt",
            description = "Deterministic evidence receipts for Java patches from any coding agent.",
            mixinStandardHelpOptions = true)
    static final class RootCommand implements Runnable {

        @CommandLine.Spec
        private CommandLine.Model.CommandSpec spec;

        @Override
        public void run() {
            spec.commandLine().usage(spec.commandLine().getOut());
        }
    }

    @CommandLine.Command(
            name = "verify",
            description = "Verify a trusted local Java 21 Maven project.",
            mixinStandardHelpOptions = true)
    final class VerifyCommand implements Callable<Integer> {

        @CommandLine.Option(names = "--project", required = true)
        private Path project;

        @CommandLine.Option(names = "--bug-report", required = true)
        private Path bugReport;

        @CommandLine.Option(names = "--patch", required = true)
        private Path patch;

        @CommandLine.Option(names = "--verifier-pack", required = true)
        private Path verifierPack;

        @CommandLine.Option(names = "--output", required = true)
        private Path output;

        @CommandLine.Option(
                names = "--allow-local-execution",
                description = "Acknowledge that Maven tests can execute arbitrary local code.")
        private boolean allowLocalExecution;

        @Override
        public Integer call() {
            if (!allowLocalExecution) {
                System.err.println("""
                        Refusing to execute the project.
                        Java builds and tests can run arbitrary code. Review this trusted project and
                        rerun with --allow-local-execution to acknowledge the risk.
                        """);
                return 2;
            }
            try {
                var verificationCase =
                        localCases.load(project, bugReport, patch, verifierPack);
                var receipt = engine.verify(
                        verificationCase,
                        stage -> System.out.printf(
                                "[%s] %s - %s%n",
                                stage.status(),
                                stage.title(),
                                stage.summary()));
                Files.createDirectories(output);
                Files.writeString(
                        output.resolve("receipt.json"),
                        json.render(receipt),
                        StandardCharsets.UTF_8);
                Files.writeString(
                        output.resolve("receipt.md"),
                        markdown.render(receipt),
                        StandardCharsets.UTF_8);
                Files.writeString(
                        output.resolve("receipt.html"),
                        html.render(receipt),
                        StandardCharsets.UTF_8);
                System.out.printf(
                        "%n%s - %s%nReceipts: %s%n",
                        receipt.verdict(),
                        receipt.verdictSummary(),
                        output.toAbsolutePath().normalize());
                return switch (receipt.verdict()) {
                    case VERIFIED -> 0;
                    case PARTIALLY_VERIFIED -> 3;
                    case REJECTED -> 4;
                };
            } catch (Exception exception) {
                System.err.println("Verification could not start: " + safeMessage(exception));
                return 1;
            }
        }
    }

    @CommandLine.Command(
            name = "init",
            description = "Scaffold a verifier-pack directory for a trusted local project.",
            mixinStandardHelpOptions = true)
    static final class InitCommand implements Callable<Integer> {

        @CommandLine.Option(names = "--output", required = true)
        private Path output;

        @Override
        public Integer call() {
            try {
                Path root = output.toAbsolutePath().normalize();
                Files.createDirectories(root);
                create(root.resolve("patchreceipt.yml"), MANIFEST_TEMPLATE);
                create(root.resolve("verifier-files.txt"), """
                        # Add verifier source paths relative to this directory, for example:
                        # src/test/java/example/DuplicateRequestReproductionTest.java
                        # src/test/java/example/ContractEdgeCases.java
                        """);
                create(root.resolve("README.md"), """
                        # PatchReceipt verifier pack

                        1. Edit `patchreceipt.yml` with the real test selectors and scope.
                        2. Put sealed JUnit tests under `src/test/java`.
                        3. List every injected source file in `verifier-files.txt`.
                        4. Write and hash the pack before evaluating candidate patches.

                        The verifier pack is compiled with the trusted local project. Do not use a
                        verifier pack you have not reviewed.
                        """);
                System.out.println("Verifier-pack scaffold created at " + root);
                return 0;
            } catch (IOException exception) {
                System.err.println("Cannot create verifier pack: " + safeMessage(exception));
                return 1;
            }
        }

        private void create(Path path, String content) throws IOException {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private static final String MANIFEST_TEMPLATE = """
            schemaVersion: 1
            caseId: local-case
            title: Trusted local patch
            summary: Replace this with the contract the patch must satisfy.
            bugReport: supplied-by-cli
            project:
              buildSystem: MAVEN
              javaRelease: 21
              filesIndex: unused-by-local-loader
              regressionTest: example.OriginalRegressionTest
            verifier:
              filesIndex: verifier-files.txt
              reproductionTest: example.DuplicateRequestReproductionTest
              edgeCaseTest: example.ContractEdgeCases
              expectedFailureType: org.opentest4j.AssertionFailedError
            mutation:
              targetClasses:
                - example.ChangedClass
              targetTests:
                - example.*
              minimumChangedLineScore: 80.0
              minimumChangedLineMutants: 2
            scope:
              expectedPaths:
                - src/main/java/example/ChangedClass.java
              forbiddenGlobs:
                - pom.xml
                - .mvn/**
                - mvnw
                - mvnw.cmd
                - .github/**
                - Dockerfile
                - src/test/**
              maximumFiles: 2
              maximumChangedLines: 50
            runtime:
              stageTimeoutSeconds: 45
              maximumLogCharacters: 24000
              maximumWorkspaceBytes: 67108864
            patches: []
            """;
}
