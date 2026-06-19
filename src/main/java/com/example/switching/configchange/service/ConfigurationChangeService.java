package com.example.switching.configchange.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.certification.service.ParticipantCertificationService;
import com.example.switching.configchange.dto.ConfigurationChangeCreateRequest;
import com.example.switching.configchange.dto.ConfigurationChangeResponse;
import com.example.switching.configchange.entity.ConfigurationChangeRequestEntity;
import com.example.switching.configchange.entity.ConfigurationChangeStatus;
import com.example.switching.configchange.entity.ConfigurationTargetType;
import com.example.switching.configchange.repository.ConfigurationChangeRequestRepository;
import com.example.switching.connector.entity.ConnectorConfigEntity;
import com.example.switching.connector.repository.ConnectorConfigRepository;
import com.example.switching.participant.entity.ParticipantEntity;
import com.example.switching.participant.enums.ParticipantStatus;
import com.example.switching.participant.repository.ParticipantRepository;
import com.example.switching.routing.entity.RoutingRuleEntity;
import com.example.switching.routing.repository.RoutingRuleRepository;

@Service
@Profile("!migration")
public class ConfigurationChangeService {

    private final ConfigurationChangeRequestRepository repository;
    private final ParticipantRepository participantRepository;
    private final ConnectorConfigRepository connectorRepository;
    private final RoutingRuleRepository routingRepository;
    private final AuditLogService auditLogService;
    private final ParticipantCertificationService participantCertificationService;

    public ConfigurationChangeService(ConfigurationChangeRequestRepository repository,
                                      ParticipantRepository participantRepository,
                                      ConnectorConfigRepository connectorRepository,
                                      RoutingRuleRepository routingRepository,
                                      AuditLogService auditLogService,
                                      ParticipantCertificationService participantCertificationService) {
        this.repository = repository;
        this.participantRepository = participantRepository;
        this.connectorRepository = connectorRepository;
        this.routingRepository = routingRepository;
        this.auditLogService = auditLogService;
        this.participantCertificationService = participantCertificationService;
    }

    @Transactional
    public ConfigurationChangeResponse request(ConfigurationChangeCreateRequest request, String actor) {
        String targetKey = normalizeKey(request.targetType(), request.targetKey());
        String previous = currentValue(request.targetType(), targetKey);
        String desired = normalizeDesired(request.targetType(), request.desiredValue());
        require(!previous.equals(desired), "desired value already active");
        ConfigurationChangeRequestEntity entity = new ConfigurationChangeRequestEntity();
        entity.setRequestRef("CCR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT));
        entity.setTargetType(request.targetType());
        entity.setTargetKey(targetKey);
        entity.setPreviousValue(previous);
        entity.setDesiredValue(desired);
        entity.setPayloadSha256(hash(request.targetType(), targetKey, previous, desired));
        entity.setReason(request.reason().trim());
        entity.setTicketReference(request.ticketReference().trim());
        entity.setStatus(ConfigurationChangeStatus.PENDING);
        entity.setRequestedBy(requireActor(actor));
        entity.setRequestedAt(LocalDateTime.now());
        entity.setExpiresAt(LocalDateTime.now().plusHours(request.validHours()));
        ConfigurationChangeRequestEntity saved = repository.save(entity);
        audit("CONFIG_CHANGE_REQUESTED", saved, actor);
        return ConfigurationChangeResponse.from(saved);
    }

    @Transactional
    public ConfigurationChangeResponse approve(Long id, String actor) {
        ConfigurationChangeRequestEntity entity = locked(id);
        requireOpen(entity, ConfigurationChangeStatus.PENDING);
        String approver = requireActor(actor);
        require(!approver.equals(entity.getRequestedBy()), "requester cannot approve own change");
        require(!LocalDateTime.now().isAfter(entity.getExpiresAt()), "change request expired");
        entity.setStatus(ConfigurationChangeStatus.APPROVED);
        entity.setApprovedBy(approver);
        entity.setApprovedAt(LocalDateTime.now());
        audit("CONFIG_CHANGE_APPROVED", entity, approver);
        return ConfigurationChangeResponse.from(repository.save(entity));
    }

    @Transactional
    public ConfigurationChangeResponse execute(Long id, String actor) {
        ConfigurationChangeRequestEntity entity = locked(id);
        requireOpen(entity, ConfigurationChangeStatus.APPROVED);
        require(!LocalDateTime.now().isAfter(entity.getExpiresAt()), "change request expired");
        String current = currentValue(entity.getTargetType(), entity.getTargetKey());
        if (!current.equals(entity.getPreviousValue())) {
            entity.setStatus(ConfigurationChangeStatus.STALE);
            repository.save(entity);
            audit("CONFIG_CHANGE_STALE", entity, actor);
            throw new IllegalStateException("target changed after request; create a new request");
        }
        require(hash(entity.getTargetType(), entity.getTargetKey(), entity.getPreviousValue(), entity.getDesiredValue())
                .equals(entity.getPayloadSha256()), "configuration request integrity check failed");
        apply(entity.getTargetType(), entity.getTargetKey(), entity.getDesiredValue());
        entity.setStatus(ConfigurationChangeStatus.EXECUTED);
        entity.setExecutedBy(requireActor(actor));
        entity.setExecutedAt(LocalDateTime.now());
        audit("CONFIG_CHANGE_EXECUTED", entity, actor);
        return ConfigurationChangeResponse.from(repository.save(entity));
    }

    @Transactional
    public ConfigurationChangeResponse reject(Long id, String actor, String reason) {
        ConfigurationChangeRequestEntity entity = locked(id);
        require(entity.getStatus() == ConfigurationChangeStatus.PENDING || entity.getStatus() == ConfigurationChangeStatus.APPROVED,
                "change request is not open");
        entity.setStatus(ConfigurationChangeStatus.REJECTED);
        entity.setRejectedBy(requireActor(actor));
        entity.setRejectedAt(LocalDateTime.now());
        entity.setRejectionReason(requireText(reason, "rejection reason"));
        audit("CONFIG_CHANGE_REJECTED", entity, actor);
        return ConfigurationChangeResponse.from(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ConfigurationChangeResponse> list(ConfigurationChangeStatus status) {
        return repository.findTop100ByStatusOrderByRequestedAtDesc(status).stream()
                .map(ConfigurationChangeResponse::from).toList();
    }

    @Scheduled(fixedDelayString = "${switching.configuration-change.expiry-scan:PT5M}")
    @Transactional
    public void expireRequests() {
        List<ConfigurationChangeRequestEntity> expired = repository
                .findByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(
                        List.of(ConfigurationChangeStatus.PENDING, ConfigurationChangeStatus.APPROVED),
                        LocalDateTime.now(), PageRequest.of(0, 200));
        for (ConfigurationChangeRequestEntity entity : expired) {
            entity.setStatus(ConfigurationChangeStatus.EXPIRED);
            audit("CONFIG_CHANGE_EXPIRED", entity, "SYSTEM");
        }
        repository.saveAll(expired);
    }

    private String currentValue(ConfigurationTargetType type, String key) {
        return switch (type) {
            case PARTICIPANT_STATUS -> participant(key).getStatus().name();
            case CONNECTOR_ENABLED -> Boolean.toString(connector(key).enabled());
            case CONNECTOR_FORCE_REJECT -> Boolean.toString(connector(key).forceReject());
            case ROUTING_RULE_ENABLED -> Boolean.toString(Boolean.TRUE.equals(route(key).getEnabled()));
        };
    }

    private void apply(ConfigurationTargetType type, String key, String desired) {
        switch (type) {
            case PARTICIPANT_STATUS -> {
                ParticipantEntity entity = participant(key);
                ParticipantStatus targetStatus = ParticipantStatus.valueOf(desired);
                if (targetStatus == ParticipantStatus.ACTIVE && !participantCertificationService.hasCurrentPass(key)) {
                    throw new IllegalStateException("participant requires a current PASS certification before activation");
                }
                entity.setStatus(targetStatus);
                participantRepository.save(entity);
            }
            case CONNECTOR_ENABLED -> {
                ConnectorConfigEntity entity = connector(key);
                entity.setEnabled(Boolean.valueOf(desired));
                connectorRepository.save(entity);
            }
            case CONNECTOR_FORCE_REJECT -> {
                ConnectorConfigEntity entity = connector(key);
                entity.setForceReject(Boolean.valueOf(desired));
                connectorRepository.save(entity);
            }
            case ROUTING_RULE_ENABLED -> {
                RoutingRuleEntity entity = route(key);
                entity.setEnabled(Boolean.valueOf(desired));
                routingRepository.save(entity);
            }
        }
    }

    private ParticipantEntity participant(String key) {
        return participantRepository.findByBankCode(key).orElseThrow(() -> new IllegalArgumentException("participant not found: " + key));
    }

    private ConnectorConfigEntity connector(String key) {
        return connectorRepository.findByConnectorName(key).orElseThrow(() -> new IllegalArgumentException("connector not found: " + key));
    }

    private RoutingRuleEntity route(String key) {
        return routingRepository.findByRouteCode(key).orElseThrow(() -> new IllegalArgumentException("routing rule not found: " + key));
    }

    private static String normalizeKey(ConfigurationTargetType type, String value) {
        String key = requireText(value, "target key");
        return type == ConfigurationTargetType.PARTICIPANT_STATUS ? key.toUpperCase(Locale.ROOT) : key;
    }

    private static String normalizeDesired(ConfigurationTargetType type, String value) {
        String desired = requireText(value, "desired value");
        if (type == ConfigurationTargetType.PARTICIPANT_STATUS) {
            return ParticipantStatus.valueOf(desired.toUpperCase(Locale.ROOT)).name();
        }
        if (!desired.equalsIgnoreCase("true") && !desired.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("boolean target requires true or false");
        }
        return Boolean.toString(Boolean.parseBoolean(desired));
    }

    private static String hash(ConfigurationTargetType type, String key, String previous, String desired) {
        try {
            String canonical = String.join("|", type.name(), key, previous, desired);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private ConfigurationChangeRequestEntity locked(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("change request not found: " + id));
    }

    private static void requireOpen(ConfigurationChangeRequestEntity entity, ConfigurationChangeStatus status) {
        require(entity.getStatus() == status, "expected status " + status + " but was " + entity.getStatus());
    }

    private void audit(String event, ConfigurationChangeRequestEntity entity, String actor) {
        auditLogService.log(event, "CONFIGURATION_CHANGE", entity.getRequestRef(), actor,
                new AuditPayload(entity.getTargetType().name(), entity.getTargetKey(), entity.getPreviousValue(),
                        entity.getDesiredValue(), entity.getPayloadSha256(), entity.getStatus().name(), entity.getTicketReference()));
    }

    private static String requireActor(String actor) {
        return requireText(actor, "authenticated actor");
    }

    private static String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record AuditPayload(String targetType, String targetKey, String previousValue, String desiredValue,
                                String payloadSha256, String status, String ticketReference) {}
}
