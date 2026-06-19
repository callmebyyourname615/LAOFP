package com.example.switching.vpa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class VpaLookupRequest {

    @NotBlank(message = "vpaType is required")
    @Pattern(regexp = "MSISDN|NATIONAL_ID|EMAIL|QR_STATIC|MERCHANT_ID",
             message = "vpaType must be one of: MSISDN, NATIONAL_ID, EMAIL, QR_STATIC, MERCHANT_ID")
    private String vpaType;

    @NotBlank(message = "vpaValue is required")
    @Size(max = 200)
    private String vpaValue;

    public VpaLookupRequest() {}

    public String getVpaType()                     { return vpaType; }
    public void   setVpaType(String v)             { this.vpaType = v; }
    public String getVpaValue()                    { return vpaValue; }
    public void   setVpaValue(String v)            { this.vpaValue = v; }
}
