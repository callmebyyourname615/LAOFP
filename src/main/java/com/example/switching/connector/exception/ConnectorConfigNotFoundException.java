package com.example.switching.connector.exception;

public class ConnectorConfigNotFoundException extends RuntimeException {

    public ConnectorConfigNotFoundException(String connectorName) {
        super("Connector config not found: " + connectorName);
    }

    public ConnectorConfigNotFoundException(String connectorName, String bankCode) {
        super("Connector config not found. connectorName="
                + connectorName
                + ", bankCode="
                + bankCode);
    }
}