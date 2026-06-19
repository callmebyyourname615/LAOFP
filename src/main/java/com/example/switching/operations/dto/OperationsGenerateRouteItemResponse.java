package com.example.switching.operations.dto;

public record OperationsGenerateRouteItemResponse(
        String sourceBank,
        String destinationBank,
        String messageType,
        String routeCode,
        String connectorName,
        String action,
        String reason
) {
}