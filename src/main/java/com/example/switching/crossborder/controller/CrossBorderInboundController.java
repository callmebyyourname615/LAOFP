package com.example.switching.crossborder.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.switching.crossborder.adapter.CrossBorderRailAdapter;
import com.example.switching.crossborder.dto.RailInstructionEvent;

@RestController
@RequestMapping("/v1/crossborder/inbound")
@ConditionalOnProperty(
        prefix = "switching.phase-ii.cross-border-adapters",
        name = "enabled",
        havingValue = "true")
public class CrossBorderInboundController {

    private static final Set<String> SUPPORTED_RAILS =
            Set.of("PROMPTPAY", "BAKONG", "NAPAS", "UPI");
    private static final int MAX_PAYLOAD_BYTES = 2 * 1024 * 1024;

    private final List<CrossBorderRailAdapter> adapters;
    private final Environment environment;

    public CrossBorderInboundController(
            List<CrossBorderRailAdapter> adapters,
            Environment environment) {
        this.adapters = List.copyOf(adapters);
        this.environment = environment;
    }

    @PostMapping("/{rail}")
    public ResponseEntity<RailInstructionEvent> inbound(
            @PathVariable String rail,
            @RequestHeader("X-Partner-Key") String partnerKey,
            @RequestHeader("X-External-Reference") String externalReference,
            @RequestHeader("X-Message-Type") String messageType,
            @RequestHeader("X-Signature") String signature,
            @RequestBody String payload) {
        String normalizedRail = normalizeRail(rail);
        verifyPartnerKey(normalizedRail, partnerKey);
        validateHeader("X-External-Reference", externalReference, 160);
        validateHeader("X-Message-Type", messageType, 64);
        if (payload == null
                || payload.isBlank()
                || payload.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Inbound rail payload is empty or too large");
        }
        CrossBorderRailAdapter adapter = adapters.stream()
                .filter(candidate -> candidate.rail().equalsIgnoreCase(normalizedRail))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Rail is not enabled"));
        return ResponseEntity.ok(adapter.acceptInbound(
                externalReference,
                messageType,
                payload,
                signature));
    }

    private void verifyPartnerKey(String rail, String supplied) {
        String property = "switching.phase-ii.cross-border-adapters."
                + rail.toLowerCase(Locale.ROOT)
                + ".inbound-api-key";
        String expected = environment.getProperty(property);
        if (expected == null || expected.length() < 32) {
            throw new IllegalStateException("Inbound partner authentication is not configured");
        }
        if (supplied == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid inbound partner credential");
        }
    }

    private static String normalizeRail(String rail) {
        String normalized = rail == null
                ? ""
                : rail.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_RAILS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported cross-border rail");
        }
        return normalized;
    }

    private static void validateHeader(String name, String value, int maxLength) {
        if (value == null
                || value.isBlank()
                || value.length() > maxLength
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
}
