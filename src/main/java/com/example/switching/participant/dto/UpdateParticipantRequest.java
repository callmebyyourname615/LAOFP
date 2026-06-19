package com.example.switching.participant.dto;

/**
 * Used for PATCH /api/participants/{bankCode}
 * All fields are optional — only non-null fields will be updated.
 */
public class UpdateParticipantRequest {

    private String bankName;
    private String status;          // ACTIVE | INACTIVE | MAINTENANCE
    private String country;
    private String currency;

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
