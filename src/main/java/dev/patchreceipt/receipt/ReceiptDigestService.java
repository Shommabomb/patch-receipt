package dev.patchreceipt.receipt;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.patchreceipt.casepack.Hashing;
import dev.patchreceipt.domain.VerificationReceipt;
import org.springframework.stereotype.Component;

@Component
public final class ReceiptDigestService {

    private final ObjectMapper canonicalMapper;

    public ReceiptDigestService() {
        this.canonicalMapper = JsonMapper.builder()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .build();
    }

    public VerificationReceipt attachDigest(VerificationReceipt receipt) {
        try {
            ObjectNode node = canonicalMapper.valueToTree(receipt);
            node.remove("receiptDigest");
            return receipt.withDigest(Hashing.sha256(canonicalMapper.writeValueAsBytes(node)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot calculate receipt digest", exception);
        }
    }
}
