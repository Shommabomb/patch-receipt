package dev.patchreceipt.casepack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public final class BundledCaseRepository {

    public static final String DEMO_CASE_ID = "checkout-coupons";
    private static final Set<String> HOSTED_PATCH_IDS = Set.of(
            "plausible-distinct",
            "correct-with-drift",
            "minimal-robust");

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public CaseManifest manifest(String caseId) {
        requireKnownCase(caseId);
        return readManifest(caseRoot(caseId) + "/patchreceipt.yml");
    }

    public List<CaseManifest> manifests() {
        return List.of(manifest(DEMO_CASE_ID));
    }

    public String bugReport(String caseId) {
        CaseManifest manifest = manifest(caseId);
        return readText(caseRoot(caseId) + "/" + manifest.bugReport());
    }

    public List<CaseManifest.PatchCandidate> hostedPatches(String caseId) {
        return manifest(caseId).patches().stream()
                .filter(candidate -> HOSTED_PATCH_IDS.contains(candidate.patchId()))
                .toList();
    }

    public VerificationCase loadHosted(String caseId, String patchId) {
        if (!HOSTED_PATCH_IDS.contains(patchId)) {
            throw new IllegalArgumentException("Unknown hosted patch: " + patchId);
        }
        return load(caseId, patchId);
    }

    public VerificationCase load(String caseId, String patchId) {
        requireKnownCase(caseId);
        String root = caseRoot(caseId);
        CaseManifest manifest = readManifest(root + "/patchreceipt.yml");
        validate(manifest);
        CaseManifest.PatchCandidate candidate = manifest.requirePatch(patchId);

        String manifestText = readText(root + "/patchreceipt.yml");
        String bugReport = readText(root + "/" + manifest.bugReport());
        String patch = readText(root + "/" + candidate.file());
        Map<String, byte[]> project = readIndexedFiles(
                root + "/project", root + "/" + manifest.project().filesIndex());
        Map<String, byte[]> verifier = readIndexedFiles(
                root + "/verifier", root + "/" + manifest.verifier().filesIndex());

        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put("manifest", Hashing.sha256(manifestText));
        hashes.put("bugReport", Hashing.sha256(bugReport));
        hashes.put("patch", Hashing.sha256(patch));
        hashes.put("project", Hashing.sha256Files(project));
        hashes.put("verifierPack", Hashing.sha256Files(verifier));

        return new VerificationCase(
                manifest, candidate, bugReport, patch, project, verifier, hashes);
    }

    private CaseManifest readManifest(String resourcePath) {
        try (InputStream stream = resource(resourcePath).getInputStream()) {
            return yamlMapper.readValue(stream, CaseManifest.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled case manifest " + resourcePath, exception);
        }
    }

    private Map<String, byte[]> readIndexedFiles(String contentRoot, String indexPath) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String path : readText(indexPath).lines()
                .map(String::trim)
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList()) {
            try (InputStream stream = resource(contentRoot + "/" + path).getInputStream()) {
                files.put(path.replace('\\', '/'), stream.readAllBytes());
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read bundled case file " + path, exception);
            }
        }
        return files;
    }

    private String readText(String resourcePath) {
        try (InputStream stream = resource(resourcePath).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled case resource " + resourcePath, exception);
        }
    }

    private ClassPathResource resource(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("Bundled case resource is missing: " + path);
        }
        return resource;
    }

    private String caseRoot(String caseId) {
        return "demo-cases/" + caseId;
    }

    private void requireKnownCase(String caseId) {
        if (!DEMO_CASE_ID.equals(caseId)) {
            throw new IllegalArgumentException("Unknown hosted case: " + caseId);
        }
    }

    private void validate(CaseManifest manifest) {
        if (manifest.schemaVersion() != 1) {
            throw new IllegalStateException("Unsupported case schema: " + manifest.schemaVersion());
        }
        if (!"MAVEN".equals(manifest.project().buildSystem())) {
            throw new IllegalStateException("Only Maven case packs are supported");
        }
        if (manifest.project().javaRelease() != 21) {
            throw new IllegalStateException("Bundled case must target Java 21");
        }
        if (manifest.patches().isEmpty()) {
            throw new IllegalStateException("Bundled case has no patches");
        }
        if (invalidSelector(manifest.project().regressionTest())
                || invalidSelector(manifest.verifier().reproductionTest())
                || invalidSelector(manifest.verifier().edgeCaseTest())) {
            throw new IllegalStateException("Bundled test selectors must name exact test classes");
        }
        if (manifest.mutation().minimumChangedLineScore() <= 0
                || manifest.mutation().minimumChangedLineScore() > 100
                || manifest.mutation().minimumChangedLineMutants() < 1) {
            throw new IllegalStateException("Bundled mutation thresholds are invalid");
        }
    }

    private boolean invalidSelector(String selector) {
        return selector == null
                || selector.isBlank()
                || selector.contains("*")
                || selector.contains("?");
    }
}
