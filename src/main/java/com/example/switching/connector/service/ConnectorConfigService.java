package com.example.switching.connector.service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.connector.dto.ConnectorConfigListResponse;
import com.example.switching.connector.dto.ConnectorConfigResponse;
import com.example.switching.connector.entity.ConnectorConfigEntity;
import com.example.switching.connector.exception.ConnectorConfigNotFoundException;
import com.example.switching.connector.repository.ConnectorConfigRepository;

@Service
public class ConnectorConfigService {

    private final ConnectorConfigRepository connectorConfigRepository;

    public ConnectorConfigService(ConnectorConfigRepository connectorConfigRepository) {
        this.connectorConfigRepository = connectorConfigRepository;
    }

    @Transactional(readOnly = true)
    public ConnectorConfigListResponse list() {
        List<ConnectorConfigResponse> items = connectorConfigRepository.findAllByOrderByConnectorNameAsc()
                .stream()
                .map(ConnectorConfigResponse::from)
                .collect(Collectors.toList());

        return new ConnectorConfigListResponse(items);
    }

    @Transactional(readOnly = true)
    public ConnectorConfigResponse getByConnectorName(String connectorName) {
        return ConnectorConfigResponse.from(requireByConnectorName(connectorName));
    }

    @Transactional(readOnly = true)
    public ConnectorConfigEntity requireByConnectorName(String connectorName) {
        String normalized = normalizeConnectorName(connectorName);

        return connectorConfigRepository.findByConnectorName(normalized)
                .orElseThrow(() -> new ConnectorConfigNotFoundException(normalized));
    }

    @Transactional(readOnly = true)
    public ConnectorConfigEntity resolveForDispatch(
            String connectorName,
            String destinationBank
    ) {
        if (StringUtils.hasText(connectorName)) {
            String normalizedConnector = normalizeConnectorName(connectorName);

            return connectorConfigRepository.findByConnectorNameAndEnabledTrue(normalizedConnector)
                    .orElseThrow(() -> new ConnectorConfigNotFoundException(
                            normalizedConnector,
                            normalizeBankCode(destinationBank)
                    ));
        }

        String normalizedBank = normalizeBankCode(destinationBank);

        return connectorConfigRepository.findByBankCodeAndEnabledTrueOrderByConnectorNameAsc(normalizedBank)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ConnectorConfigNotFoundException(null, normalizedBank));
    }

    private String normalizeConnectorName(String connectorName) {
        if (!StringUtils.hasText(connectorName)) {
            throw new IllegalArgumentException("connectorName is required");
        }

        return connectorName.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeBankCode(String bankCode) {
        if (!StringUtils.hasText(bankCode)) {
            throw new IllegalArgumentException("bankCode is required");
        }

        return bankCode.trim().toUpperCase(Locale.ROOT);
    }
}