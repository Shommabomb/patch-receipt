package dev.patchreceipt.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class WorkspaceManager {

    private final Path root;
    private final boolean keepWorkspaces;

    public WorkspaceManager(
            @Value("${patchreceipt.workspace-root:${user.dir}/.patchreceipt-work}") String root,
            @Value("${patchreceipt.runner.keep-workspaces:false}") boolean keepWorkspaces) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.keepWorkspaces = keepWorkspaces;
    }

    public Path create(String receiptId) throws IOException {
        Files.createDirectories(root);
        Path workspace = root.resolve(receiptId).normalize();
        if (!workspace.startsWith(root) || workspace.equals(root)) {
            throw new IOException("Invalid workspace path");
        }
        Files.createDirectory(workspace);
        return workspace;
    }

    public long size(Path workspace) throws IOException {
        try (var files = Files.walk(workspace)) {
            return files.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException exception) {
                            return 0L;
                        }
                    })
                    .sum();
        }
    }

    public void cleanup(Path workspace) {
        if (keepWorkspaces || workspace == null) {
            return;
        }
        Path normalized = workspace.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) {
            throw new IllegalArgumentException("Refusing to remove workspace outside configured root");
        }
        try (var paths = Files.walk(normalized)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            // Cleanup failure is logged by the caller's runtime; evidence is already in memory.
        }
    }

    public String sanitize(String text, Path workspace) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
                .replace(workspace.toString(), "<workspace>")
                .replace(workspace.toString().replace('\\', '/'), "<workspace>");
    }
}
