package com.example.switching.rtp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeclineRtpRequest(@NotBlank @Size(max = 500) String reason) {
}
