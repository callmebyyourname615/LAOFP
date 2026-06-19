package com.example.switching.vpa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class VpaRegisterRequest {

    @NotBlank(message = "vpaType is required")
    @Pattern(regexp = "MSISDN|NATIONAL_ID|EMAIL|QR_STATIC|MERCHANT_ID",
             message = "vpaType must be one of: MSISDN, NATIONAL_ID, EMAIL, QR_STATIC, MERCHANT_ID")
    private String vpaType;

    @NotBlank(message = "vpaValue is required")
    @Size(max = 200)
    private String vpaValue;

    @NotBlank(message = "pspId is required")
    @Size(max = 32)
    private String pspId;

    @NotBlank(message = "accountRef is required")
    @Size(max = 200)
    private String accountRef;

    @Pattern(regexp = "BANK_ACCOUNT|WALLET|MERCHANT_ACCOUNT",
             message = "accountType must be one of: BANK_ACCOUNT, WALLET, MERCHANT_ACCOUNT")
    private String accountType = "BANK_ACCOUNT";

    @Size(max = 200)
    private String displayName;

    public VpaRegisterRequest() {}

    public String getVpaType()                     { return vpaType; }
    public void   setVpaType(String v)             { this.vpaType = v; }
    public String getVpaValue()                    { return vpaValue; }
    public void   setVpaValue(String v)            { this.vpaValue = v; }
    public String getPspId()                       { return pspId; }
    public void   setPspId(String v)               { this.pspId = v; }
    public String getAccountRef()                  { return accountRef; }
    public void   setAccountRef(String v)          { this.accountRef = v; }
    public String getAccountType()                 { return accountType; }
    public void   setAccountType(String v)         { this.accountType = v; }
    public String getDisplayName()                 { return displayName; }
    public void   setDisplayName(String v)         { this.displayName = v; }
}
