package dev.patchreceipt.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.patchreceipt.casepack.BundledCaseRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RunApiControllerTests {

    private RunRegistry runs;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        BundledCaseRepository cases = mock(BundledCaseRepository.class);
        runs = mock(RunRegistry.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new RunApiController(cases, runs))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void startsOnlyAValidatedRunRequest() throws Exception {
        when(runs.start("checkout-coupons", "minimal-robust"))
                .thenReturn(queued());

        mvc.perform(post("/api/v1/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "checkout-coupons",
                                  "patchId": "minimal-robust"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string(
                        "Location", "/api/v1/runs/run-123"))
                .andExpect(jsonPath("$.state").value("QUEUED"));
    }

    @Test
    void rejectsMissingIdsAndUnknownHostedIds() throws Exception {
        mvc.perform(post("/api/v1/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        when(runs.start(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Unknown hosted patch"));
        mvc.perform(post("/api/v1/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseId":"checkout-coupons","patchId":"raw-user-patch"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown hosted patch"));
    }

    @Test
    void reportsQueueSaturationAndExpiredRuns() throws Exception {
        when(runs.start(anyString(), anyString()))
                .thenThrow(new QueueFullException());
        mvc.perform(post("/api/v1/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseId":"checkout-coupons","patchId":"minimal-robust"}
                                """))
                .andExpect(status().isTooManyRequests());

        when(runs.find("missing"))
                .thenThrow(new NoSuchElementException("Unknown or expired run: missing"));
        mvc.perform(get("/api/v1/runs/missing"))
                .andExpect(status().isNotFound());
    }

    private RunSnapshot queued() {
        return new RunSnapshot(
                "run-123",
                "checkout-coupons",
                "minimal-robust",
                RunState.QUEUED,
                "Waiting for the verifier",
                "2026-07-25T00:00:00Z",
                null,
                null,
                null,
                List.of(),
                null);
    }
}
