package com.example.switching.usermgmt.dto;

import jakarta.validation.constraints.Size;

public record MakerCheckerDecisionRequest(@Size(max = 512) String notes) {}
