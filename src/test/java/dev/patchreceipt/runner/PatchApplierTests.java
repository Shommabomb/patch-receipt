package dev.patchreceipt.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatchApplierTests {

    private final PatchApplier applier = new PatchApplier();

    @TempDir
    Path project;

    @Test
    void preservesLfLineEndingsWhileApplyingPatch() throws Exception {
        Path source = source("one\ntwo\nthree\n");

        applier.apply(project, patch());

        byte[] result = Files.readAllBytes(source);
        assertThat(result).doesNotContain((byte) '\r');
        assertThat(Files.readString(source)).isEqualTo("one\nchanged\nthree\n");
    }

    @Test
    void preservesCrlfLineEndingsWhileApplyingPatch() throws Exception {
        Path source = source("one\r\ntwo\r\nthree\r\n");

        applier.apply(project, patch());

        byte[] result = Files.readAllBytes(source);
        for (int index = 0; index < result.length; index++) {
            if (result[index] == '\n') {
                assertThat(index).isPositive();
                assertThat(result[index - 1]).isEqualTo((byte) '\r');
            }
        }
        assertThat(Files.readString(source)).isEqualTo("one\r\nchanged\r\nthree\r\n");
    }

    private Path source(String content) throws Exception {
        Path source = project.resolve("src/main/java/App.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
        return source;
    }

    private String patch() {
        return """
                diff --git a/src/main/java/App.java b/src/main/java/App.java
                --- a/src/main/java/App.java
                +++ b/src/main/java/App.java
                @@ -1,3 +1,3 @@
                 one
                -two
                +changed
                 three
                """;
    }
}
