package com.example.switching.qr.dto;

import jakarta.validation.constraints.NotBlank;

public record DecodeQrRequest(@NotBlank String qrPayload) {}
