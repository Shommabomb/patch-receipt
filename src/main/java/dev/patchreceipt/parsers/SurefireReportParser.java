package dev.patchreceipt.parsers;

import dev.patchreceipt.domain.TestEvidence;
import dev.patchreceipt.domain.TestFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

@Component
public final class SurefireReportParser {

    public TestEvidence parse(Path project, String requestedClass, long durationMs) throws IOException {
        if (requestedClass == null
                || requestedClass.isBlank()
                || requestedClass.contains("*")
                || requestedClass.contains("?")) {
            throw new IOException("Surefire selector must name an exact test class");
        }
        Path reports = project.resolve("target/surefire-reports");
        if (!Files.isDirectory(reports)) {
            return new TestEvidence(0, 0, 0, 0, 0, durationMs, List.of());
        }

        int tests = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        List<TestFailure> failureDetails = new ArrayList<>();

        try (var paths = Files.list(reports)) {
            for (Path report : paths
                    .filter(path -> path.getFileName().toString().startsWith("TEST-"))
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .toList()) {
                Element suite = XmlSupport.parse(report).getDocumentElement();
                String suiteName = suite.getAttribute("name");
                if (!matches(requestedClass, suiteName, report.getFileName().toString())) {
                    continue;
                }
                tests += integer(suite, "tests");
                failures += integer(suite, "failures");
                errors += integer(suite, "errors");
                skipped += integer(suite, "skipped");

                var testCases = suite.getElementsByTagName("testcase");
                for (int index = 0; index < testCases.getLength(); index++) {
                    Element testCase = (Element) testCases.item(index);
                    collectFailure(testCase, "failure", failureDetails);
                    collectFailure(testCase, "error", failureDetails);
                }
            }
        }

        return new TestEvidence(
                tests,
                Math.max(0, tests - failures - errors - skipped),
                failures,
                errors,
                skipped,
                durationMs,
                failureDetails);
    }

    private void collectFailure(
            Element testCase,
            String childName,
            List<TestFailure> target) {
        var nodes = testCase.getElementsByTagName(childName);
        for (int index = 0; index < nodes.getLength(); index++) {
            Element failure = (Element) nodes.item(index);
            target.add(new TestFailure(
                    testCase.getAttribute("classname"),
                    testCase.getAttribute("name"),
                    failure.getAttribute("type"),
                    firstNonBlank(failure.getAttribute("message"), failure.getTextContent().strip())));
        }
    }

    private boolean matches(String requestedClass, String suiteName, String fileName) {
        return requestedClass.equals(suiteName)
                || fileName.equals("TEST-" + requestedClass + ".xml")
                || suiteName.startsWith(requestedClass + "$");
    }

    private int integer(Element element, String attribute) {
        String value = element.getAttribute(attribute);
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
