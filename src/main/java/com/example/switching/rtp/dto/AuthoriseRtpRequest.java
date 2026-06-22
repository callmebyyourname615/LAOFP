package com.example.switching.rtp.dto;

import java.math.BigDecimal;
import java.util.List;
import com.example.switching.rtp.enums.RtpAuthorisationMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuthoriseRtpRequest(
        @NotBlank @Size(max = 64) String authorisationReference,
        @NotNull RtpAuthorisationMode mode,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal authorisedAmount,
        @Size(max = 64) String inquiryRef,
        @Valid List<RtpInstallmentRequest> installments) {
}
