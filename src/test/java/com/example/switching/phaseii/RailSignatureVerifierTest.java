package com.example.switching.phaseii;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import com.example.switching.crossborder.service.RailSignatureVerifier;

class RailSignatureVerifierTest {
    @Test
    void acceptsValidHmacAndRejectsTampering() throws Exception {
        String payload="{\"internalRef\":\"TX-1\"}";
        String secret="test-secret-at-least-32-characters";
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signature=HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        RailSignatureVerifier verifier=new RailSignatureVerifier();
        assertDoesNotThrow(() -> verifier.verifyHmacSha256(payload,signature,secret));
        assertThrows(SecurityException.class, () -> verifier.verifyHmacSha256(payload+"x",signature,secret));
    }
}
