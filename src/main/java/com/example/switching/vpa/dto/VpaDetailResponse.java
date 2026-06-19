package com.example.switching.vpa.dto;

import java.time.LocalDateTime;

/** Full VPA record — returned by GET /v1/lookup/vpa/{vpaId}. */
public class VpaDetailResponse {

    private String vpaId;
    private String vpaType;
    private String vpaValue;
    private String pspId;
    private String accountRef;
    private String accountType;
    private String displayName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public VpaDetailResponse() {}

    public VpaDetailResponse(String vpaId, String vpaType, String vpaValue,
                              String pspId, String accountRef, String accountType,
                              String displayName, String status,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.vpaId       = vpaId;
        this.vpaType     = vpaType;
        this.vpaValue    = vpaValue;
        this.pspId       = pspId;
        this.accountRef  = accountRef;
        this.accountType = accountType;
        this.displayName = displayName;
        this.status      = status;
        this.createdAt   = createdAt;
        this.updatedAt   = updatedAt;
    }

    public String getVpaId()                       { return vpaId; }
    public void   setVpaId(String v)               { this.vpaId = v; }
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
    public String getStatus()                      { return status; }
    public void   setStatus(String v)              { this.status = v; }
    public LocalDateTime getCreatedAt()            { return createdAt; }
    public void   setCreatedAt(LocalDateTime v)    { this.createdAt = v; }
    public LocalDateTime getUpdatedAt()            { return updatedAt; }
    public void   setUpdatedAt(LocalDateTime v)    { this.updatedAt = v; }
}
