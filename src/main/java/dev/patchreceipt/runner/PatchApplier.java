package dev.patchreceipt.runner;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Component;

@Component
public final class PatchApplier {

    public void apply(Path project, String patch) throws GitAPIException {
        try (Git git = Git.init().setDirectory(project.toFile()).call()) {
            git.apply()
                    .setPatch(new ByteArrayInputStream(patch.getBytes(StandardCharsets.UTF_8)))
                    .call();
        }
    }
}
