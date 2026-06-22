package com.example.switching.rtp.dto;

import jakarta.validation.constraints.Size;

public record CancelRtpRequest(@Size(max = 500) String reason) {
}
