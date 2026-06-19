package com.example.switching.connector.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.connector.dto.ConnectorConfigResponse;
import com.example.switching.connector.dto.CreateConnectorConfigRequest;
import com.example.switching.connector.dto.UpdateConnectorConfigRequest;
import com.example.switching.connector.entity.ConnectorConfigEntity;
import com.example.switching.connector.enums.ConnectorType;
import com.example.switching.connector.exception.ConnectorConfigAlreadyExistsException;
import com.example.switching.connector.exception.ConnectorConfigNotFoundException;
import com.example.switching.connector.repository.ConnectorConfigRepository;
import com.example.switching.participant.service.ParticipantService;

@Service
public class ConnectorConfigManagementService {

    private static final int DEFAULT_TIMEOUT_MS = 5000;

    private final ConnectorConfigRepository connectorConfigRepository;
    private final ParticipantService participantService;

    public ConnectorConfigManagementService(
            ConnectorConfigRepository connectorConfigRepository,
            ParticipantService participantService) {
        this.connectorConfigRepository = connectorConfigRepository;
        this.participantService = participantService;
    }

    @Transactional
    public ConnectorConfigResponse create(CreateConnectorConfigRequest request) {
        requireField(request.getConnectorName(), "connectorName");
        requireField(request.getBankCode(), "bankCode");
        requireField(request.getConnectorType(), "connectorType");

        String connectorName = normalizeCode(request.getConnectorName());
        String bankCode = normalizeCode(request.getBankCode());
        ConnectorType connectorType = parseConnectorType(request.getConnectorType());
        Integer timeoutMs = request.getTimeoutMs() != null ? request.getTimeoutMs() : DEFAULT_TIMEOUT_MS;

        validateTimeoutMs(timeoutMs);

        if (connectorConfigRepository.findByConnectorName(connectorName).isPresent()) {
            throw new ConnectorConfigAlreadyExistsException(connectorName);
        }

        participantService.findByBankCode(bankCode);

        ConnectorConfigEntity entity = new ConnectorConfigEntity();
        entity.setConnectorName(connectorName);
        entity.setBankCode(bankCode);
        entity.setConnectorType(connectorType);
        entity.setEndpointUrl(normalizeNullableText(request.getEndpointUrl()));
        entity.setTimeoutMs(timeoutMs);
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        entity.setForceReject(request.getForceReject() != null ? request.getForceReject() : false);
        entity.setRejectReasonCode(normalizeNullableText(request.getRejectReasonCode()));
        entity.setRejectReasonMessage(normalizeNullableText(request.getRejectReasonMessage()));

        ConnectorConfigEntity saved = connectorConfigRepository.save(entity);

        return ConnectorConfigResponse.from(saved);
    }

    @Transactional
    public ConnectorConfigResponse update(String connectorName, UpdateConnectorConfigRequest request) {
        String normalizedConnectorName = normalizeCode(connectorName);

        ConnectorConfigEntity entity = connectorConfigRepository.findByConnectorName(normalizedConnectorName)
                .orElseThrow(() -> new ConnectorConfigNotFoundException(normalizedConnectorName));

        if (request.getEndpointUrl() != null) {
            entity.setEndpointUrl(normalizeNullableText(request.getEndpointUrl()));
        }

        if (request.getTimeoutMs() != null) {
            validateTimeoutMs(request.getTimeoutMs());
            entity.setTimeoutMs(request.getTimeoutMs());
        }

        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }

        if (request.getForceReject() != null) {
            entity.setForceReject(request.getForceReject());
        }

        if (request.getRejectReasonCode() != null) {
            entity.setRejectReasonCode(normalizeNullableText(request.getRejectReasonCode()));
        }

        if (request.getRejectReasonMessage() != null) {
            entity.setRejectReasonMessage(normalizeNullableText(request.getRejectReasonMessage()));
        }

        ConnectorConfigEntity saved = connectorConfigRepository.save(entity);

        return ConnectorConfigResponse.from(saved);
    }

    private ConnectorType parseConnectorType(String raw) {
        try {
            return ConnectorType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid connectorType: " + raw + ". Valid values: MOCK, HTTP, MQ");
        }
    }

    private void validateTimeoutMs(Integer timeoutMs) {
        if (timeoutMs == null || timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be greater than zero");
        }
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("value is required");
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private void requireField(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
