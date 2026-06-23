package com.example.switching.usermgmt.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface ControlledActionHandler {
    boolean supports(String requestType);
    String requiredPermission();
    String execute(JsonNode payload, String checkerUsername);
}
