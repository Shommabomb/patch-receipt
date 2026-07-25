package dev.patchreceipt.casepack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public record VerificationCase(
        CaseManifest manifest,
        CaseManifest.PatchCandidate candidate,
        String bugReport,
        String patch,
        Map<String, byte[]> projectFiles,
        Map<String, byte[]> verifierFiles,
        Map<String, String> hashes) {

    public VerificationCase {
        projectFiles = immutableByteMap(projectFiles);
        verifierFiles = immutableByteMap(verifierFiles);
        hashes = Map.copyOf(hashes);
    }

    public void materializeProject(Path destination) throws IOException {
        writeFiles(destination, projectFiles);
    }

    public void injectVerifier(Path destination) throws IOException {
        writeFiles(destination, verifierFiles);
    }

    private static void writeFiles(Path destination, Map<String, byte[]> files) throws IOException {
        for (var entry : files.entrySet()) {
            Path target = destination.resolve(entry.getKey()).normalize();
            if (!target.startsWith(destination.normalize())) {
                throw new IOException("Case file escapes destination: " + entry.getKey());
            }
            Files.createDirectories(target.getParent());
            Files.write(target, entry.getValue());
        }
    }

    private static Map<String, byte[]> immutableByteMap(Map<String, byte[]> source) {
        Map<String, byte[]> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, value.clone()));
        return java.util.Collections.unmodifiableMap(copy);
    }
}
