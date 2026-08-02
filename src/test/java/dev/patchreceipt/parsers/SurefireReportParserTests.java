package dev.patchreceipt.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import dev.patchreceipt.domain.TestEvidence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SurefireReportParserTests {

    private final SurefireReportParser parser = new SurefireReportParser();

    @TempDir
    Path project;

    @Test
    void returnsEmptyEvidenceWhenReportDirectoryIsMissing() throws IOException {
        TestEvidence evidence =
                parser.parse(project, "dev.example.MissingTest", 37);

        assertThat(evidence.tests()).isZero();
        assertThat(evidence.passed()).isZero();
        assertThat(evidence.failureDetails()).isEmpty();
        assertThat(evidence.durationMs()).isEqualTo(37);
        assertThat(evidence.successful()).isFalse();
    }

    @Test
    void aggregatesMatchingSuiteAndCapturesFailuresAndErrors() throws IOException {
        writeReport("TEST-dev.example.CalculatorTest.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="dev.example.CalculatorTest"
                           tests="4" failures="1" errors="1" skipped="1">
                  <testcase classname="dev.example.CalculatorTest" name="passes"/>
                  <testcase classname="dev.example.CalculatorTest" name="fails">
                    <failure type="org.opentest4j.AssertionFailedError"
                             message="expected: &lt;10&gt; but was: &lt;9&gt;">stack</failure>
                  </testcase>
                  <testcase classname="dev.example.CalculatorTest" name="errors">
                    <error type="java.lang.IllegalStateException">
                      calculation was unavailable
                    </error>
                  </testcase>
                  <testcase classname="dev.example.CalculatorTest" name="skipped">
                    <skipped/>
                  </testcase>
                </testsuite>
                """);
        writeReport("TEST-dev.example.UnrelatedTest.xml", """
                <testsuite name="dev.example.UnrelatedTest"
                           tests="10" failures="0" errors="0" skipped="0"/>
                """);

        TestEvidence evidence =
                parser.parse(project, "dev.example.CalculatorTest", 125);

        assertThat(evidence.tests()).isEqualTo(4);
        assertThat(evidence.passed()).isOne();
        assertThat(evidence.failures()).isOne();
        assertThat(evidence.errors()).isOne();
        assertThat(evidence.skipped()).isOne();
        assertThat(evidence.durationMs()).isEqualTo(125);
        assertThat(evidence.successful()).isFalse();
        assertThat(evidence.failureDetails())
                .extracting(
                        failure -> failure.testName(),
                        failure -> failure.type(),
                        failure -> failure.message())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "fails",
                                "org.opentest4j.AssertionFailedError",
                                "expected: <10> but was: <9>"),
                        org.assertj.core.groups.Tuple.tuple(
                                "errors",
                                "java.lang.IllegalStateException",
                                "calculation was unavailable"));
    }

    @Test
    void includesNestedSuiteWhenRequestedClassOwnsIt() throws IOException {
        writeReport("TEST-dev.example.DynamicCases$Generated.xml", """
                <testsuite name="dev.example.DynamicCases$Generated"
                           tests="2" failures="0" errors="0" skipped="0">
                  <testcase classname="dev.example.DynamicCases$Generated" name="case-one"/>
                  <testcase classname="dev.example.DynamicCases$Generated" name="case-two"/>
                </testsuite>
                """);

        TestEvidence evidence =
                parser.parse(project, "dev.example.DynamicCases", 8);

        assertThat(evidence.tests()).isEqualTo(2);
        assertThat(evidence.passed()).isEqualTo(2);
        assertThat(evidence.successful()).isTrue();
    }

    @Test
    void skippedMandatorySuiteIsNotSuccessful() throws IOException {
        writeReport("TEST-dev.example.SkippedTest.xml", """
                <testsuite name="dev.example.SkippedTest"
                           tests="1" failures="0" errors="0" skipped="1">
                  <testcase classname="dev.example.SkippedTest" name="skipped">
                    <skipped/>
                  </testcase>
                </testsuite>
                """);

        TestEvidence evidence =
                parser.parse(project, "dev.example.SkippedTest", 8);

        assertThat(evidence.tests()).isOne();
        assertThat(evidence.passed()).isZero();
        assertThat(evidence.skipped()).isOne();
        assertThat(evidence.successful()).isFalse();
    }

    @Test
    void rejectsBlankOrWildcardSelectors() {
        assertThatIOException()
                .isThrownBy(() -> parser.parse(project, "", 0))
                .withMessageContaining("exact test class");
        assertThatIOException()
                .isThrownBy(() -> parser.parse(project, "dev.example.*", 0))
                .withMessageContaining("exact test class");
        assertThatIOException()
                .isThrownBy(() -> parser.parse(project, "dev.example.Test?", 0))
                .withMessageContaining("exact test class");
    }

    @Test
    void rejectsCorruptXmlReport() throws IOException {
        writeReport("TEST-dev.example.BrokenTest.xml", """
                <testsuite name="dev.example.BrokenTest" tests="1">
                  <testcase name="broken">
                </testsuite>
                """);

        assertThatIOException()
                .isThrownBy(() -> parser.parse(project, "dev.example.BrokenTest", 0))
                .withMessageContaining("Cannot parse XML report");
    }

    private void writeReport(String fileName, String xml) throws IOException {
        Path reports = Files.createDirectories(project.resolve("target/surefire-reports"));
        Files.writeString(reports.resolve(fileName), xml);
    }
}
