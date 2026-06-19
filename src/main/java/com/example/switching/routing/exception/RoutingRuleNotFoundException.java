package com.example.switching.routing.exception;

public class RoutingRuleNotFoundException extends RuntimeException {

    public RoutingRuleNotFoundException(
            String sourceBank,
            String destinationBank,
            String messageType
    ) {
        super("Routing rule not found. sourceBank="
                + sourceBank
                + ", destinationBank="
                + destinationBank
                + ", messageType="
                + messageType);
    }
}