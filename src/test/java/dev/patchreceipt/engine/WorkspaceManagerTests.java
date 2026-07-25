package dev.patchreceipt.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceManagerTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void cleanupRemovesWorkspaceContainingReadOnlyFiles() throws Exception {
        Path root = temporaryDirectory.resolve("work");
        WorkspaceManager manager = new WorkspaceManager(root.toString(), false);
        Path workspace = manager.create("receipt-1");
        Path gitObject = workspace.resolve("patched/.git/objects/aa/object");
        Files.createDirectories(gitObject.getParent());
        Files.writeString(gitObject, "fixture");

        DosFileAttributeView attributes =
                Files.getFileAttributeView(gitObject, DosFileAttributeView.class);
        if (attributes != null) {
            attributes.setReadOnly(true);
        }

        manager.cleanup(workspace);

        assertThat(workspace).doesNotExist();
    }

    @Test
    void cleanupRefusesPathsOutsideConfiguredRoot() {
        Path root = temporaryDirectory.resolve("work");
        WorkspaceManager manager = new WorkspaceManager(root.toString(), false);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> manager.cleanup(temporaryDirectory.resolve("elsewhere")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside configured root");
    }
}
