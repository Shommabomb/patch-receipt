package dev.patchreceipt.receipt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.patchreceipt.domain.VerificationReceipt;
import org.springframework.stereotype.Component;

@Component
public final class JsonReceiptRenderer {

    private final ObjectMapper objectMapper;

    public JsonReceiptRenderer() {
        this.objectMapper = new ObjectMapper();
    }

    public String render(VerificationReceipt receipt) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(receipt);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot render JSON receipt", exception);
        }
    }
}
