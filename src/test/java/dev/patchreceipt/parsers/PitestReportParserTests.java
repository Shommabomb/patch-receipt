package dev.patchreceipt.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import dev.patchreceipt.domain.MutationEvidence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PitestReportParserTests {

    private static final String CALCULATOR_PATH =
            "src/main/java/dev/example/Calculator.java";

    private final PitestReportParser parser = new PitestReportParser();

    @TempDir
    Path project;

    @Test
    void rejectsMissingMutationReport() {
        assertThatIOException()
                .isThrownBy(() -> parser.parse(
                        project,
                        Map.of(CALCULATOR_PATH, Set.of(12)),
                        80.0,
                        2))
                .withMessageContaining("report is missing");
    }

    @Test
    void scoresOnlyViableMutantsOnChangedLinesAndClassifiesStatuses() throws IOException {
        writeMutationReport("""
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations>
                  %s
                  %s
                  %s
                  %s
                  %s
                  %s
                  %s
                  %s
                  %s
                </mutations>
                """.formatted(
                mutation("KILLED", "dev.example.Calculator", 12, "testKills"),
                mutation("SURVIVED", "dev.example.Calculator", 13, ""),
                mutation("NO_COVERAGE", "dev.example.Calculator", 14, ""),
                mutation("TIMED_OUT", "dev.example.Calculator$Helper", 15, ""),
                mutation("RUN_ERROR", "dev.example.Calculator", 16, ""),
                mutation("NON_VIABLE", "dev.example.Calculator", 17, ""),
                mutation("EQUIVALENT", "dev.example.Calculator", 18, ""),
                mutation("NEW_STATUS", "dev.example.Calculator", 19, ""),
                mutation("KILLED", "dev.example.OtherClass", 12, "otherTest")));

        MutationEvidence evidence = parser.parse(
                project,
                Map.of(CALCULATOR_PATH, Set.of(12, 13, 14, 15, 16, 17, 18, 19)),
                80.0,
                2);

        assertThat(evidence.provenance()).isEqualTo("LIVE");
        assertThat(evidence.totalMutants()).isEqualTo(9);
        assertThat(evidence.changedLineMutants()).isEqualTo(6);
        assertThat(evidence.killed()).isOne();
        assertThat(evidence.survived()).isEqualTo(2);
        assertThat(evidence.uncovered()).isOne();
        assertThat(evidence.timedOutOrErrored()).isEqualTo(2);
        assertThat(evidence.changedLineScore()).isCloseTo(
                16.6666666667,
                org.assertj.core.data.Offset.offset(0.0000001));
        assertThat(evidence.requiredScore()).isEqualTo(80.0);
        assertThat(evidence.requiredChangedLineMutants()).isEqualTo(2);
        assertThat(evidence.conclusive()).isFalse();
        assertThat(evidence.survivingMutants())
                .extracting(finding -> finding.status())
                .containsExactly(
                        "SURVIVED",
                        "NO_COVERAGE",
                        "TIMED_OUT",
                        "RUN_ERROR",
                        "NEW_STATUS");
    }

    @Test
    void reportsConclusiveEvidenceWhenChangedLineMutantsAreKilled() throws IOException {
        writeMutationReport("""
                <mutations>
                  %s
                  %s
                </mutations>
                """.formatted(
                mutation("KILLED", "dev.example.Calculator", 8, "firstTest"),
                mutation("KILLED", "dev.example.Calculator", 9, "secondTest")));

        MutationEvidence evidence = parser.parse(
                project,
                Map.of(CALCULATOR_PATH, Set.of(8, 9)),
                80.0,
                2);

        assertThat(evidence.changedLineMutants()).isEqualTo(2);
        assertThat(evidence.killed()).isEqualTo(2);
        assertThat(evidence.changedLineScore()).isEqualTo(100.0);
        assertThat(evidence.conclusive()).isTrue();
        assertThat(evidence.survivingMutants()).isEmpty();
    }

    @Test
    void listsEveryChangedProductionFileWithoutViableMutationEvidence() throws IOException {
        writeMutationReport("""
                <mutations>
                  %s
                </mutations>
                """.formatted(
                mutation("KILLED", "dev.example.Calculator", 8, "firstTest")));

        MutationEvidence evidence = parser.parse(
                project,
                Map.of(
                        CALCULATOR_PATH, Set.of(8),
                        "src/main/java/dev/example/Unmutated.java", Set.of(4)),
                80.0,
                2);

        assertThat(evidence.filesWithoutMutants())
                .containsExactly("src/main/java/dev/example/Unmutated.java");
        assertThat(evidence.conclusive()).isTrue();
    }

    @Test
    void nonStandardJavaSourceRootCannotSilentlyBorrowAnotherFilesMutants()
            throws IOException {
        writeMutationReport("""
                <mutations>
                  %s
                  %s
                </mutations>
                """.formatted(
                mutation("KILLED", "dev.example.Calculator", 8, "firstTest"),
                mutation("KILLED", "dev.example.Calculator", 9, "secondTest")));

        MutationEvidence evidence = parser.parse(
                project,
                Map.of(
                        CALCULATOR_PATH, Set.of(8, 9),
                        "core/src/main/java/dev/example/Other.java", Set.of(4)),
                80.0,
                2);

        assertThat(evidence.filesWithoutMutants())
                .containsExactly("core/src/main/java/dev/example/Other.java");
    }

    @Test
    void rejectsPartialMutationRecordEvenWhenAnotherRecordWouldScorePerfectly()
            throws IOException {
        writeMutationReport("""
                <mutations>
                  %s
                  <mutation status="KILLED">
                    <mutatedMethod>calculate</mutatedMethod>
                    <lineNumber>9</lineNumber>
                    <mutator>MathMutator</mutator>
                  </mutation>
                </mutations>
                """.formatted(
                mutation("KILLED", "dev.example.Calculator", 8, "firstTest")));

        assertThatIOException()
                .isThrownBy(() -> parser.parse(
                        project,
                        Map.of(CALCULATOR_PATH, Set.of(8, 9)),
                        80.0,
                        2))
                .withMessageContaining("mutatedClass");
    }

    @Test
    void rejectsCorruptMutationXml() throws IOException {
        writeMutationReport("""
                <mutations>
                  <mutation status="KILLED">
                </mutations>
                """);

        assertThatIOException()
                .isThrownBy(() -> parser.parse(
                        project,
                        Map.of(CALCULATOR_PATH, Set.of(1)),
                        80.0,
                        2))
                .withMessageContaining("Cannot parse XML report");
    }

    private void writeMutationReport(String xml) throws IOException {
        Path reports = Files.createDirectories(project.resolve("target/pit-reports"));
        Files.writeString(reports.resolve("mutations.xml"), xml);
    }

    private String mutation(
            String status,
            String mutatedClass,
            int line,
            String killingTest) {
        return """
                <mutation status="%s">
                  <sourceFile>Calculator.java</sourceFile>
                  <mutatedClass>%s</mutatedClass>
                  <mutatedMethod>calculate</mutatedMethod>
                  <methodDescription>()I</methodDescription>
                  <lineNumber>%d</lineNumber>
                  <mutator>org.pitest.mutationtest.engine.gregor.mutators.MathMutator</mutator>
                  <indexes><index>0</index></indexes>
                  <blocks><block>0</block></blocks>
                  <killingTest>%s</killingTest>
                  <description>Replaced integer addition with subtraction</description>
                </mutation>
                """.formatted(status, mutatedClass, line, killingTest);
    }
}
