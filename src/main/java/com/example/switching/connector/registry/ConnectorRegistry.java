package com.example.switching.connector.registry;

import org.springframework.stereotype.Component;

import com.example.switching.connector.BankConnector;
import com.example.switching.connector.GenericHttpConnector;
import com.example.switching.connector.GenericMockConnector;
import com.example.switching.connector.GenericMqConnector;
import com.example.switching.connector.entity.ConnectorConfigEntity;
import com.example.switching.connector.service.ConnectorConfigService;

/**
 * Resolves the correct {@link BankConnector} implementation for a given connector name.
 * <p>
 * The registry looks up {@code connector_configs} by {@code connectorName},
 * reads the {@code connector_type}, and returns the matching connector bean:
 * <ul>
 *   <li>MOCK → {@link GenericMockConnector}</li>
 *   <li>HTTP → {@link GenericHttpConnector}</li>
 *   <li>MQ   → {@link GenericMqConnector}</li>
 * </ul>
 * <p>
 * This design means the outbox worker never references a concrete connector class directly.
 * Adding support for 100-200 member banks only requires new rows in {@code connector_configs},
 * not new connector classes.
 */
@Component
public class ConnectorRegistry {

    private final ConnectorConfigService connectorConfigService;
    private final GenericMockConnector genericMockConnector;
    private final GenericHttpConnector genericHttpConnector;
    private final GenericMqConnector genericMqConnector;

    public ConnectorRegistry(
            ConnectorConfigService connectorConfigService,
            GenericMockConnector genericMockConnector,
            GenericHttpConnector genericHttpConnector,
            GenericMqConnector genericMqConnector) {
        this.connectorConfigService = connectorConfigService;
        this.genericMockConnector = genericMockConnector;
        this.genericHttpConnector = genericHttpConnector;
        this.genericMqConnector = genericMqConnector;
    }

    /**
     * Resolve the BankConnector implementation for the given connector name.
     *
     * @param connectorName the connector name stored in connector_configs (e.g. MOCK_BANK_B_CONNECTOR)
     * @return the BankConnector implementation appropriate for this connector's type
     * @throws com.example.switching.connector.exception.ConnectorConfigNotFoundException
     *         if the connectorName is not found in connector_configs
     * @throws UnsupportedConnectorTypeException
     *         if the connector_type has no matching implementation yet
     */
    public BankConnector resolve(String connectorName) {
        ConnectorConfigEntity config = connectorConfigService.requireByConnectorName(connectorName);

        return switch (config.getConnectorType()) {
            case MOCK -> genericMockConnector;
            case HTTP -> genericHttpConnector;
            case MQ   -> genericMqConnector;
        };
    }
}
