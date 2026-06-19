package com.example.switching.vpa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class VpaUpdateRequest {

    @NotBlank(message = "accountRef is required")
    @Size(max = 200)
    private String accountRef;

    @Size(max = 200)
    private String displayName;

    public VpaUpdateRequest() {}

    public String getAccountRef()                  { return accountRef; }
    public void   setAccountRef(String v)          { this.accountRef = v; }
    public String getDisplayName()                 { return displayName; }
    public void   setDisplayName(String v)         { this.displayName = v; }
}
