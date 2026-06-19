package com.example.switching.connector.exception;

public class ConnectorConfigAlreadyExistsException extends RuntimeException {

    public ConnectorConfigAlreadyExistsException(String connectorName) {
        super("Connector config already exists: " + connectorName);
    }
}
