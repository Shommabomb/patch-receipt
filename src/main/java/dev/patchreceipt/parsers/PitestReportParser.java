package dev.patchreceipt.parsers;

import dev.patchreceipt.domain.MutationEvidence;
import dev.patchreceipt.domain.MutationFinding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    public MutationEvidence parse(
            Path project,
            Map<String, Set<Integer>> changedLinesByPath,
            double requiredScore) throws IOException {
        Path report = project.resolve("target/pit-reports/mutations.xml");
        if (!Files.isRegularFile(report)) {
            return inconclusive(requiredScore);
        }

        var nodes = XmlSupport.parse(report).getDocumentElement().getElementsByTagName("mutation");
        int total = nodes.getLength();
        int changed = 0;
        int killed = 0;
        int survived = 0;
        int uncovered = 0;
        int timedOutOrErrored = 0;
        List<MutationFinding> survivors = new ArrayList<>();

        for (int index = 0; index < nodes.getLength(); index++) {
            Element mutation = (Element) nodes.item(index);
            String status = mutation.getAttribute("status");
            String mutatedClass = text(mutation, "mutatedClass");
            int line = integer(text(mutation, "lineNumber"));
            String sourcePath = "src/main/java/"
                    + mutatedClass.replace('.', '/').replaceFirst("\\$.*$", "")
                    + ".java";
            if (!changedLinesByPath.getOrDefault(sourcePath, Set.of()).contains(line)
                    || NON_VIABLE.contains(status)) {
                continue;
            }

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
        return new MutationEvidence(
                "LIVE",
                total,
                changed,
                killed,
                survived,
                uncovered,
                timedOutOrErrored,
                round(score),
                requiredScore,
                changed > 0 && timedOutOrErrored == 0,
                survivors);
    }

    private MutationEvidence inconclusive(double requiredScore) {
        return new MutationEvidence(
                "NOT_AVAILABLE", 0, 0, 0, 0, 0, 0,
                0.0, requiredScore, false, List.of());
    }

    private String text(Element parent, String tagName) {
        var nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().strip();
    }

    private int integer(String value) {
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
