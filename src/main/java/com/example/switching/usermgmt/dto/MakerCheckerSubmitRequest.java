package com.example.switching.usermgmt.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MakerCheckerSubmitRequest(@NotBlank String requestType, @NotNull JsonNode payload) {}
