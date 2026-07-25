package dev.patchreceipt.web;

import dev.patchreceipt.domain.VerificationReceipt;
import dev.patchreceipt.receipt.HtmlReceiptRenderer;
import dev.patchreceipt.receipt.JsonReceiptRenderer;
import dev.patchreceipt.receipt.MarkdownReceiptRenderer;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runs/{runId}")
public final class ReceiptApiController {

    private static final MediaType MARKDOWN =
            new MediaType("text", "markdown", StandardCharsets.UTF_8);

    private final RunRegistry runs;
    private final JsonReceiptRenderer json;
    private final MarkdownReceiptRenderer markdown;
    private final HtmlReceiptRenderer html;

    public ReceiptApiController(
            RunRegistry runs,
            JsonReceiptRenderer json,
            MarkdownReceiptRenderer markdown,
            HtmlReceiptRenderer html) {
        this.runs = runs;
        this.json = json;
        this.markdown = markdown;
        this.html = html;
    }

    @GetMapping("/receipt.json")
    ResponseEntity<String> json(@PathVariable String runId) {
        VerificationReceipt receipt = runs.receipt(runId);
        return response(
                json.render(receipt),
                MediaType.APPLICATION_JSON,
                filename(receipt, "json"),
                true);
    }

    @GetMapping("/receipt.md")
    ResponseEntity<String> markdown(@PathVariable String runId) {
        VerificationReceipt receipt = runs.receipt(runId);
        return response(
                markdown.render(receipt),
                MARKDOWN,
                filename(receipt, "md"),
                true);
    }

    @GetMapping("/receipt.html")
    ResponseEntity<String> html(@PathVariable String runId) {
        VerificationReceipt receipt = runs.receipt(runId);
        return response(
                html.render(receipt),
                MediaType.TEXT_HTML,
                filename(receipt, "html"),
                false);
    }

    private ResponseEntity<String> response(
            String body,
            MediaType contentType,
            String filename,
            boolean attachment) {
        ContentDisposition disposition = ContentDisposition
                .builder(attachment ? "attachment" : "inline")
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    private String filename(VerificationReceipt receipt, String extension) {
        return "patchreceipt-%s-%s.%s"
                .formatted(receipt.caseId(), receipt.patchId(), extension);
    }
}
