package com.example.switching.usermgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record MakerCheckerSubmitRequest(@NotBlank String requestType, @NotNull Map<String, Object> payload) {}
