package dev.patchreceipt.casepack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.patchreceipt.domain.Verdict;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class LocalCaseLoader {

    private static final int MAXIMUM_FILES = 1_000;
    private static final long MAXIMUM_INPUT_BYTES = 8L * 1024 * 1024;
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".cache", ".patchreceipt-work", "target", "build", ".idea");

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public VerificationCase load(
            Path project,
            Path bugReport,
            Path patch,
            Path verifierPack) throws IOException {
        Path projectRoot = requireDirectory(project, "project");
        Path verifierRoot = requireDirectory(verifierPack, "verifier pack");
        Path manifestPath = requireFile(
                verifierRoot.resolve("patchreceipt.yml"), "verifier manifest");
        Path bugReportPath = requireFile(bugReport, "bug report");
        Path patchPath = requireFile(patch, "patch");

        String manifestText = Files.readString(manifestPath, StandardCharsets.UTF_8);
        CaseManifest manifest = yamlMapper.readValue(manifestText, CaseManifest.class);
        validate(manifest);

        Map<String, byte[]> projectFiles = readProject(projectRoot);
        Map<String, byte[]> verifierFiles = readVerifierFiles(verifierRoot, manifest);
        String bugReportText = Files.readString(bugReportPath, StandardCharsets.UTF_8);
        String patchText = Files.readString(patchPath, StandardCharsets.UTF_8);
        enforceTotalSize(
                projectFiles,
                verifierFiles,
                manifestText.length() + bugReportText.length() + patchText.length());

        CaseManifest.PatchCandidate candidate = new CaseManifest.PatchCandidate(
                "local-patch",
                patchPath.getFileName().toString(),
                "Trusted local unified diff",
                patchPath.toString(),
                Verdict.PARTIALLY_VERIFIED);
        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put("manifest", Hashing.sha256(manifestText));
        hashes.put("bugReport", Hashing.sha256(bugReportText));
        hashes.put("patch", Hashing.sha256(patchText));
        hashes.put("project", Hashing.sha256Files(projectFiles));
        hashes.put("verifierPack", Hashing.sha256Files(verifierFiles));

        return new VerificationCase(
                manifest,
                candidate,
                bugReportText,
                patchText,
                projectFiles,
                verifierFiles,
                hashes);
    }

    private Map<String, byte[]> readProject(Path root) throws IOException {
        if (!Files.isRegularFile(root.resolve("pom.xml"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The trusted local project must contain pom.xml");
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                Path relative = root.relativize(file);
                if (excluded(relative)) {
                    continue;
                }
                rejectSymbolicPath(root, relative);
                addFile(files, relative, file);
            }
        }
        return files;
    }

    private Map<String, byte[]> readVerifierFiles(
            Path root,
            CaseManifest manifest) throws IOException {
        Path index = requireFile(root.resolve(manifest.verifier().filesIndex()), "verifier index");
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String entry : Files.readAllLines(index, StandardCharsets.UTF_8)) {
            String normalizedEntry = entry.trim().replace('\\', '/');
            if (normalizedEntry.isBlank() || normalizedEntry.startsWith("#")) {
                continue;
            }
            Path relative = Path.of(normalizedEntry).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) {
                throw new IOException("Verifier path escapes its pack: " + entry);
            }
            rejectSymbolicPath(root, relative);
            addFile(files, relative, requireFile(root.resolve(relative), "verifier file"));
        }
        if (files.isEmpty()) {
            throw new IOException("The verifier pack contains no indexed test files");
        }
        return files;
    }

    private void addFile(Map<String, byte[]> files, Path relative, Path file) throws IOException {
        if (files.size() >= MAXIMUM_FILES) {
            throw new IOException("Local input exceeds " + MAXIMUM_FILES + " files");
        }
        byte[] bytes = Files.readAllBytes(file);
        files.put(relative.toString().replace('\\', '/'), bytes);
    }

    private void rejectSymbolicPath(Path root, Path relative) throws IOException {
        Path current = root;
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not accepted in local inputs: " + relative);
            }
        }
    }

    private boolean excluded(Path relative) {
        for (Path part : relative) {
            if (EXCLUDED_DIRECTORIES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private void enforceTotalSize(
            Map<String, byte[]> project,
            Map<String, byte[]> verifier,
            long textBytes) throws IOException {
        long total = textBytes;
        total += project.values().stream().mapToLong(value -> value.length).sum();
        total += verifier.values().stream().mapToLong(value -> value.length).sum();
        if (total > MAXIMUM_INPUT_BYTES) {
            throw new IOException("Local input exceeds the 8 MiB MVP limit");
        }
    }

    private Path requireDirectory(Path path, String label) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing " + label + " directory: " + normalized);
        }
        return normalized;
    }

    private Path requireFile(Path path, String label) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing " + label + ": " + normalized);
        }
        return normalized;
    }

    private void validate(CaseManifest manifest) throws IOException {
        if (manifest.schemaVersion() != 1) {
            throw new IOException("Unsupported verifier schema: " + manifest.schemaVersion());
        }
        if (manifest.project() == null
                || !"MAVEN".equals(manifest.project().buildSystem())
                || manifest.project().javaRelease() != 21) {
            throw new IOException("The MVP accepts only Java 21 Maven verifier manifests");
        }
        if (manifest.verifier() == null
                || manifest.scope() == null
                || manifest.mutation() == null
                || manifest.runtime() == null) {
            throw new IOException("Verifier manifest is missing required policy sections");
        }
        if (manifest.runtime().stageTimeoutSeconds() < 1
                || manifest.runtime().stageTimeoutSeconds() > 120) {
            throw new IOException("Stage timeout must be between 1 and 120 seconds");
        }
        if (invalidExactSelector(manifest.project().regressionTest())
                || invalidExactSelector(manifest.verifier().reproductionTest())
                || invalidExactSelector(manifest.verifier().edgeCaseTest())) {
            throw new IOException("Test selectors must name exact test classes");
        }
        if (manifest.mutation().targetClasses().stream().anyMatch(this::invalidMutationTarget)
                || manifest.mutation().targetTests().stream().anyMatch(this::invalidMutationTarget)) {
            throw new IOException("Mutation targets cannot be blank or wildcard-only");
        }
        if (manifest.mutation().minimumChangedLineScore() <= 0
                || manifest.mutation().minimumChangedLineScore() > 100
                || manifest.mutation().minimumChangedLineMutants() < 1) {
            throw new IOException(
                    "Mutation score must be within (0, 100] and mutant count must be positive");
        }
    }

    private boolean invalidExactSelector(String selector) {
        return selector == null
                || selector.isBlank()
                || selector.contains("*")
                || selector.contains("?");
    }

    private boolean invalidMutationTarget(String selector) {
        return selector == null
                || selector.isBlank()
                || "*".equals(selector.strip());
    }
}
