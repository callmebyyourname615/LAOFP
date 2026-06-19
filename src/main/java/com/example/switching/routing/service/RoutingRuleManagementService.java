package com.example.switching.routing.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.connector.service.ConnectorConfigService;
import com.example.switching.iso.enums.IsoMessageType;
import com.example.switching.participant.service.ParticipantService;
import com.example.switching.routing.dto.CreateRoutingRuleRequest;
import com.example.switching.routing.dto.RoutingRuleResponse;
import com.example.switching.routing.dto.UpdateRoutingRuleRequest;
import com.example.switching.routing.entity.RoutingRuleEntity;
import com.example.switching.routing.exception.RoutingRuleAlreadyExistsException;
import com.example.switching.routing.exception.RoutingRuleNotFoundException;
import com.example.switching.routing.repository.RoutingRuleRepository;

@Service
public class RoutingRuleManagementService {

    private final RoutingRuleRepository routingRuleRepository;
    private final ParticipantService participantService;
    private final ConnectorConfigService connectorConfigService;
    private final RoutingService routingService;

    public RoutingRuleManagementService(
            RoutingRuleRepository routingRuleRepository,
            ParticipantService participantService,
            ConnectorConfigService connectorConfigService,
            RoutingService routingService) {
        this.routingRuleRepository = routingRuleRepository;
        this.participantService = participantService;
        this.connectorConfigService = connectorConfigService;
        this.routingService = routingService;
    }

    @Transactional
    public RoutingRuleResponse create(CreateRoutingRuleRequest request) {
        requireField(request.getRouteCode(), "routeCode");
        requireField(request.getSourceBank(), "sourceBank");
        requireField(request.getDestinationBank(), "destinationBank");
        requireField(request.getMessageType(), "messageType");
        requireField(request.getConnectorName(), "connectorName");

        String routeCode = normalizeCode(request.getRouteCode());
        String sourceBank = normalizeCode(request.getSourceBank());
        String destinationBank = normalizeCode(request.getDestinationBank());
        String connectorName = normalizeCode(request.getConnectorName());
        IsoMessageType messageType = parseMessageType(request.getMessageType());
        Integer priority = request.getPriority() != null ? request.getPriority() : 1;

        validatePriority(priority);

        if (routingRuleRepository.findByRouteCode(routeCode).isPresent()) {
            throw new RoutingRuleAlreadyExistsException(routeCode);
        }

        participantService.findByBankCode(sourceBank);
        participantService.findByBankCode(destinationBank);
        connectorConfigService.requireByConnectorName(connectorName);

        RoutingRuleEntity entity = new RoutingRuleEntity();
        entity.setRouteCode(routeCode);
        entity.setSourceBank(sourceBank);
        entity.setDestinationBank(destinationBank);
        entity.setMessageType(messageType);
        entity.setConnectorName(connectorName);
        entity.setPriority(priority);
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);

        RoutingRuleEntity saved = routingRuleRepository.save(entity);

        routingService.clearCache();

        return RoutingRuleResponse.from(saved);
    }

    @Transactional
    public RoutingRuleResponse update(String routeCode, UpdateRoutingRuleRequest request) {
        String normalizedRouteCode = normalizeCode(routeCode);

        RoutingRuleEntity entity = routingRuleRepository.findByRouteCode(normalizedRouteCode)
                .orElseThrow(() -> new RoutingRuleNotFoundException(normalizedRouteCode, "-", "-"));

        if (request.getConnectorName() != null) {
            if (StringUtils.hasText(request.getConnectorName())) {
                String connectorName = normalizeCode(request.getConnectorName());
                connectorConfigService.requireByConnectorName(connectorName);
                entity.setConnectorName(connectorName);
            }
        }

        if (request.getPriority() != null) {
            validatePriority(request.getPriority());
            entity.setPriority(request.getPriority());
        }

        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }

        RoutingRuleEntity saved = routingRuleRepository.save(entity);

        routingService.clearCache();

        return RoutingRuleResponse.from(saved);
    }

    private IsoMessageType parseMessageType(String raw) {
        try {
            return IsoMessageType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid messageType: " + raw + ". Valid values: PACS_008, PACS_002, PACS_028, PACS_004");
        }
    }

    private void validatePriority(Integer priority) {
        if (priority == null || priority <= 0) {
            throw new IllegalArgumentException("priority must be greater than zero");
        }
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("value is required");
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void requireField(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
