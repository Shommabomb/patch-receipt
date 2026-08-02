package dev.patchreceipt.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patchreceipt.domain.StageResult;
import dev.patchreceipt.domain.StageStatus;
import dev.patchreceipt.domain.TestEvidence;
import dev.patchreceipt.domain.TestFailure;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvidenceSanitizerTests {

    @Test
    void masksEveryKnownAbsoluteRootAcrossReceiptEvidence() {
        String application = "C:\\Users\\reviewer\\patch-receipt";
        String maven = application + "\\.cache\\maven";
        String user = "C:\\Users\\reviewer";
        Path workspace = Path.of(application, ".patchreceipt-work", "receipt");
        EvidenceSanitizer sanitizer = new EvidenceSanitizer(application, maven, user);

        List<StageResult> stages = sanitizer.stages(
                List.of(new StageResult(
                        "engine",
                        "Engine",
                        StageStatus.ERROR,
                        0,
                        "Failed under " + workspace,
                        Map.of(
                                "command", "-Dmaven.repo.local=" + maven,
                                "nested", List.of(Map.of(
                                        "path",
                                        application.toLowerCase() + "\\src\\Nested.java"))),
                        "home=" + user.replace('\\', '/').toUpperCase())),
                workspace);
        TestEvidence tests = sanitizer.tests(
                new TestEvidence(
                        1,
                        0,
                        1,
                        0,
                        0,
                        0,
                        List.of(new TestFailure(
                                "Test",
                                "fails",
                                "AssertionError",
                                "source " + application + "\\src\\App.java"))),
                workspace);

        String combined = stages + " " + tests;
        assertThat(combined)
                .contains("<workspace>", "<maven-home>", "<user-home>", "<application-root>")
                .doesNotContain("C:\\Users\\reviewer")
                .doesNotContain("C:/Users/reviewer")
                .doesNotContain("c:\\users\\reviewer")
                .doesNotContain("C:/USERS/REVIEWER");
    }

    @Test
    void masksUrlEncodedFileUriPaths() {
        String application = "C:\\Users\\Example User\\patch-receipt";
        String maven = application + "\\.cache\\maven";
        String user = "C:\\Users\\Example User";
        EvidenceSanitizer sanitizer = new EvidenceSanitizer(application, maven, user);

        String log = "loaded from file:/C:/Users/Example%20User/patch-receipt/"
                + ".cache/maven/repository/library.jar";

        assertThat(sanitizer.text(log, null))
                .contains("file:/<maven-home>/repository/library.jar")
                .doesNotContain("Example%20User")
                .doesNotContain("C:/Users");
    }
}
