package com.example.switching.routing.exception;

public class RoutingRuleAlreadyExistsException extends RuntimeException {

    public RoutingRuleAlreadyExistsException(String routeCode) {
        super("Routing rule already exists: " + routeCode);
    }
}
