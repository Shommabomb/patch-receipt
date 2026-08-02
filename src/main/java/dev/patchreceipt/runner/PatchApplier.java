package dev.patchreceipt.runner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Component;

@Component
public final class PatchApplier {

    private static final Pattern NEW_FILE_HEADER =
            Pattern.compile("^\\+\\+\\+ b/(.+?)(?:\\t.*)?$");

    public void apply(Path project, String patch) throws GitAPIException {
        Map<Path, LineEndings> originalLineEndings = lineEndings(project, patch);
        try (Git git = Git.init().setDirectory(project.toFile()).call()) {
            git.getRepository().getConfig().setBoolean("core", null, "autocrlf", false);
            try {
                git.getRepository().getConfig().save();
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(
                        "Cannot pin verifier Git line-ending behaviour",
                        exception);
            }
            git.apply()
                    .setPatch(new ByteArrayInputStream(patch.getBytes(StandardCharsets.UTF_8)))
                    .call();
        }
        restoreLineEndings(originalLineEndings);
    }

    private Map<Path, LineEndings> lineEndings(Path project, String patch) {
        Map<Path, LineEndings> styles = new LinkedHashMap<>();
        Path root = project.toAbsolutePath().normalize();
        for (String line : patch.split("\\R")) {
            Matcher matcher = NEW_FILE_HEADER.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            Path target = root.resolve(matcher.group(1)).normalize();
            if (!target.startsWith(root) || !Files.isRegularFile(target)) {
                continue;
            }
            try {
                styles.put(target, detectLineEndings(Files.readAllBytes(target)));
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(
                        "Cannot inspect patch target line endings",
                        exception);
            }
        }
        return Map.copyOf(styles);
    }

    private void restoreLineEndings(Map<Path, LineEndings> styles) {
        for (var entry : styles.entrySet()) {
            if (entry.getValue() == LineEndings.MIXED
                    || entry.getValue() == LineEndings.NONE
                    || !Files.isRegularFile(entry.getKey())) {
                continue;
            }
            try {
                byte[] content = Files.readAllBytes(entry.getKey());
                byte[] normalized = entry.getValue() == LineEndings.CRLF
                        ? toCrlf(content)
                        : toLf(content);
                if (!java.util.Arrays.equals(content, normalized)) {
                    Files.write(entry.getKey(), normalized);
                }
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(
                        "Cannot restore patch target line endings",
                        exception);
            }
        }
    }

    private LineEndings detectLineEndings(byte[] content) {
        int lineFeeds = 0;
        int crlf = 0;
        for (int index = 0; index < content.length; index++) {
            if (content[index] == '\n') {
                lineFeeds++;
                if (index > 0 && content[index - 1] == '\r') {
                    crlf++;
                }
            }
        }
        if (lineFeeds == 0) {
            return LineEndings.NONE;
        }
        if (crlf == lineFeeds) {
            return LineEndings.CRLF;
        }
        if (crlf == 0) {
            return LineEndings.LF;
        }
        return LineEndings.MIXED;
    }

    private byte[] toLf(byte[] content) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(content.length);
        for (int index = 0; index < content.length; index++) {
            if (content[index] == '\r'
                    && index + 1 < content.length
                    && content[index + 1] == '\n') {
                continue;
            }
            output.write(content[index]);
        }
        return output.toByteArray();
    }

    private byte[] toCrlf(byte[] content) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(content.length);
        for (int index = 0; index < content.length; index++) {
            if (content[index] == '\n'
                    && (index == 0 || content[index - 1] != '\r')) {
                output.write('\r');
            }
            output.write(content[index]);
        }
        return output.toByteArray();
    }

    private enum LineEndings {
        LF,
        CRLF,
        MIXED,
        NONE
    }
}
