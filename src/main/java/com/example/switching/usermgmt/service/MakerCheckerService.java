package com.example.switching.usermgmt.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.switching.audit.service.AuditLogService;
import com.example.switching.usermgmt.dto.MakerCheckerResponse;
import com.example.switching.usermgmt.entity.MakerCheckerRequestEntity;
import com.example.switching.usermgmt.entity.UserEntity;
import com.example.switching.usermgmt.enums.MakerCheckerStatus;
import com.example.switching.usermgmt.repository.MakerCheckerRequestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class MakerCheckerService {
    private final MakerCheckerRequestRepository requests;
    private final AuthorizationService authorization;
    private final List<ControlledActionHandler> handlers;
    private final ObjectMapper mapper;
    private final AuditLogService audit;

    public MakerCheckerService(MakerCheckerRequestRepository requests,
            AuthorizationService authorization, List<ControlledActionHandler> handlers,
            ObjectMapper mapper, AuditLogService audit) {
        this.requests = requests; this.authorization = authorization;
        this.handlers = List.copyOf(handlers); this.mapper = mapper; this.audit = audit;
    }

    @Transactional
    public MakerCheckerResponse submit(String requestType, JsonNode payload, String makerUsername) {
        String normalizedType = requestType.trim().toUpperCase(Locale.ROOT);
        ControlledActionHandler handler = handler(normalizedType);
        UserEntity maker = authorization.requireUser(makerUsername);
        authorization.requirePermission(maker, "maker_checker.submit");
        authorization.requirePermission(maker, handler.requiredPermission());
        JsonNode normalizedPayload = canonicalNode(payload);
        String canonical = canonical(normalizedPayload);
        MakerCheckerRequestEntity request = new MakerCheckerRequestEntity();
        request.setId(UUID.randomUUID()); request.setRequestType(normalizedType);
        request.setPayloadJson(normalizedPayload); request.setPayloadSha256(sha256(canonical));
        request.setMaker(maker); request.setStatus(MakerCheckerStatus.PENDING);
        MakerCheckerRequestEntity saved = requests.save(request);
        audit.log("SMOS_MAKER_CHECKER_SUBMITTED", "SMOS_MAKER_CHECKER", saved.getId().toString(), makerUsername,
                Map.of("requestType", normalizedType, "payloadSha256", saved.getPayloadSha256()));
        return response(saved);
    }

    @Transactional(readOnly = true)
    public List<MakerCheckerResponse> pending() {
        return requests.findTop100ByStatusOrderBySubmittedAtAsc(MakerCheckerStatus.PENDING).stream()
                .map(this::response).toList();
    }

    @Transactional
    public MakerCheckerResponse approve(UUID id, String checkerUsername, String notes) {
        MakerCheckerRequestEntity request = requests.findWithLockById(id)
                .orElseThrow(() -> new IllegalArgumentException("Maker-checker request not found: " + id));
        requirePending(request);
        UserEntity checker = authorization.requireUser(checkerUsername);
        if (request.getMaker().getId().equals(checker.getId())) {
            throw new IllegalStateException("Maker and checker must be different users");
        }
        ControlledActionHandler handler = handler(request.getRequestType());
        authorization.requirePermission(checker, "maker_checker.approve");
        authorization.requirePermission(checker, handler.requiredPermission());
        String reference = handler.execute(readPayload(request), checkerUsername);
        request.setChecker(checker); request.setStatus(MakerCheckerStatus.APPROVED);
        request.setDecidedAt(Instant.now()); request.setDecisionNotes(notes);
        request.setExecutionReference(reference);
        audit.log("SMOS_MAKER_CHECKER_APPROVED", "SMOS_MAKER_CHECKER", id.toString(), checkerUsername,
                Map.of("requestType", request.getRequestType(), "executionReference", reference));
        return response(requests.save(request));
    }

    @Transactional
    public MakerCheckerResponse reject(UUID id, String checkerUsername, String notes) {
        MakerCheckerRequestEntity request = requests.findWithLockById(id)
                .orElseThrow(() -> new IllegalArgumentException("Maker-checker request not found: " + id));
        requirePending(request);
        UserEntity checker = authorization.requireUser(checkerUsername);
        if (request.getMaker().getId().equals(checker.getId())) throw new IllegalStateException("Maker and checker must be different users");
        authorization.requirePermission(checker, "maker_checker.approve");
        request.setChecker(checker); request.setStatus(MakerCheckerStatus.REJECTED);
        request.setDecidedAt(Instant.now()); request.setDecisionNotes(notes);
        audit.log("SMOS_MAKER_CHECKER_REJECTED", "SMOS_MAKER_CHECKER", id.toString(), checkerUsername,
                Map.of("requestType", request.getRequestType()));
        return response(requests.save(request));
    }

    private ControlledActionHandler handler(String type) {
        return handlers.stream().filter(candidate -> candidate.supports(type)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported controlled request type: " + type));
    }
    private void requirePending(MakerCheckerRequestEntity request) {
        if (request.getStatus() != MakerCheckerStatus.PENDING) throw new IllegalStateException("Request is not pending");
    }
    private String canonical(JsonNode payload) {
        try { return mapper.writeValueAsString(payload); }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid request payload", ex); }
    }
    private JsonNode readPayload(MakerCheckerRequestEntity request) {
        JsonNode stored = request.getPayloadJson();
        if (stored == null) throw new IllegalStateException("Stored request payload is missing");
        JsonNode normalized = canonicalNode(stored);
        String actualHash = sha256(canonical(normalized));
        if (!MessageDigest.isEqual(actualHash.getBytes(StandardCharsets.US_ASCII),
                request.getPayloadSha256().getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalStateException("Stored request payload integrity check failed");
        }
        return normalized.deepCopy();
    }

    private JsonNode canonicalNode(JsonNode node) {
        if (node == null || node.isNull()) return mapper.nullNode();
        if (node.isObject()) {
            ObjectNode object = mapper.createObjectNode();
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
            sorted.forEach((name, value) -> object.set(name, canonicalNode(value)));
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = mapper.createArrayNode();
            node.forEach(value -> array.add(canonicalNode(value)));
            return array;
        }
        return node.deepCopy();
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
    private MakerCheckerResponse response(MakerCheckerRequestEntity request) {
        return new MakerCheckerResponse(request.getId(), request.getRequestType(), readPayload(request), request.getPayloadSha256(),
                request.getMaker().getUsername(), request.getChecker() == null ? null : request.getChecker().getUsername(),
                request.getStatus(), request.getSubmittedAt(), request.getDecidedAt(), request.getDecisionNotes(), request.getExecutionReference());
    }
}
