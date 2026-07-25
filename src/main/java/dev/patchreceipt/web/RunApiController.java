package dev.patchreceipt.web;

import dev.patchreceipt.casepack.BundledCaseRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public final class RunApiController {

    private final BundledCaseRepository cases;
    private final RunRegistry runs;

    public RunApiController(BundledCaseRepository cases, RunRegistry runs) {
        this.cases = cases;
        this.runs = runs;
    }

    @GetMapping("/cases")
    public List<HostedCaseResponse> cases() {
        return cases.manifests().stream()
                .map(manifest -> new HostedCaseResponse(
                        manifest.caseId(),
                        manifest.title(),
                        manifest.summary(),
                        cases.bugReport(manifest.caseId()),
                        cases.hostedPatches(manifest.caseId()).stream()
                                .map(candidate -> {
                                    var loaded = cases.load(manifest.caseId(), candidate.patchId());
                                    return new HostedCaseResponse.HostedPatchResponse(
                                            candidate.patchId(),
                                            candidate.title(),
                                            candidate.description(),
                                            loaded.patch());
                                })
                                .toList()))
                .toList();
    }

    @PostMapping("/runs")
    public ResponseEntity<RunSnapshot> start(@Valid @RequestBody StartRunRequest request) {
        RunSnapshot snapshot = runs.start(request.caseId(), request.patchId());
        return ResponseEntity
                .accepted()
                .location(URI.create("/api/v1/runs/" + snapshot.runId()))
                .body(snapshot);
    }

    @GetMapping("/runs/{runId}")
    public RunSnapshot find(@PathVariable String runId) {
        return runs.find(runId);
    }

    public record StartRunRequest(
            @NotBlank String caseId,
            @NotBlank String patchId) {
    }
}
