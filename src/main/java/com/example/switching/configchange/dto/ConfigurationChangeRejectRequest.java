package com.example.switching.configchange.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfigurationChangeRejectRequest(@NotBlank @Size(max = 1000) String reason) {}
