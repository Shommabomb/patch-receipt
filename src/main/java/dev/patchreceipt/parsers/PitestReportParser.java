package dev.patchreceipt.parsers;

import dev.patchreceipt.domain.MutationEvidence;
import dev.patchreceipt.domain.MutationFinding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

@Component
public final class PitestReportParser {

    private static final Set<String> NON_VIABLE = Set.of("NON_VIABLE", "EQUIVALENT");
    private static final Set<String> ERROR_STATUSES =
            Set.of("TIMED_OUT", "RUN_ERROR", "MEMORY_ERROR");
    private static final Set<String> SUPPORTED_STATUSES = Set.of(
            "KILLED",
            "SURVIVED",
            "NO_COVERAGE",
            "TIMED_OUT",
            "RUN_ERROR",
            "MEMORY_ERROR",
            "NON_VIABLE",
            "EQUIVALENT");

    public MutationEvidence parse(
            Path project,
            Map<String, Set<Integer>> changedLinesByPath,
            double requiredScore,
            int requiredChangedLineMutants) throws IOException {
        Path report = project.resolve("target/pit-reports/mutations.xml");
        if (!Files.isRegularFile(report)) {
            throw new IOException("Mutation report is missing");
        }

        Element root = XmlSupport.parse(report).getDocumentElement();
        if (root == null || !"mutations".equals(root.getTagName())) {
            throw new IOException("Mutation report has an unexpected root element");
        }
        var nodes = root.getElementsByTagName("mutation");
        int total = nodes.getLength();
        int changed = 0;
        int killed = 0;
        int survived = 0;
        int uncovered = 0;
        int timedOutOrErrored = 0;
        boolean reportComplete = true;
        List<MutationFinding> survivors = new ArrayList<>();
        Map<String, Integer> viableMutantsByPath = new LinkedHashMap<>();
        changedLinesByPath.keySet().stream()
                .filter(PitestReportParser::isJavaSourcePath)
                .sorted()
                .forEach(path -> viableMutantsByPath.put(path, 0));

        for (int index = 0; index < nodes.getLength(); index++) {
            Element mutation = (Element) nodes.item(index);
            String status = requiredAttribute(mutation, "status");
            String mutatedClass = requiredText(mutation, "mutatedClass");
            int line = positiveInteger(requiredText(mutation, "lineNumber"));
            requiredText(mutation, "mutatedMethod");
            requiredText(mutation, "mutator");
            if (!SUPPORTED_STATUSES.contains(status)) {
                reportComplete = false;
            }
            String sourcePath = "src/main/java/"
                    + mutatedClass.replace('.', '/').replaceFirst("\\$.*$", "")
                    + ".java";
            if (!changedLinesByPath.getOrDefault(sourcePath, Set.of()).contains(line)
                    || NON_VIABLE.contains(status)) {
                continue;
            }

            viableMutantsByPath.computeIfPresent(sourcePath, (path, count) -> count + 1);
            changed++;
            switch (status) {
                case "KILLED" -> killed++;
                case "SURVIVED" -> survived++;
                case "NO_COVERAGE" -> uncovered++;
                default -> {
                    if (ERROR_STATUSES.contains(status)) {
                        timedOutOrErrored++;
                    } else {
                        survived++;
                    }
                }
            }

            if (!"KILLED".equals(status)) {
                survivors.add(new MutationFinding(
                        status,
                        mutatedClass,
                        text(mutation, "mutatedMethod"),
                        line,
                        text(mutation, "mutator"),
                        text(mutation, "description"),
                        text(mutation, "killingTest")));
            }
        }

        double score = changed == 0 ? 0.0 : killed * 100.0 / changed;
        List<String> filesWithoutMutants = viableMutantsByPath.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .toList();
        return new MutationEvidence(
                "LIVE",
                true,
                total,
                changed,
                killed,
                survived,
                uncovered,
                timedOutOrErrored,
                score,
                requiredScore,
                requiredChangedLineMutants,
                changed > 0 && timedOutOrErrored == 0 && reportComplete,
                filesWithoutMutants,
                survivors);
    }

    private static boolean isJavaSourcePath(String path) {
        return path.endsWith(".java")
                && !path.startsWith("src/test/")
                && !path.contains("/src/test/");
    }

    private String text(Element parent, String tagName) {
        var nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().strip();
    }

    private String requiredAttribute(Element element, String name) throws IOException {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) {
            throw new IOException("Mutation report is missing required attribute: " + name);
        }
        return value.strip();
    }

    private String requiredText(Element element, String tagName) throws IOException {
        String value = text(element, tagName);
        if (value.isBlank()) {
            throw new IOException("Mutation report is missing required element: " + tagName);
        }
        return value;
    }

    private int positiveInteger(String value) throws IOException {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IOException("Mutation report line number must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IOException("Mutation report contains an invalid line number", exception);
        }
    }

}
