package com.example.switching.settlement.dto;

import java.time.LocalDate;

public class OpenCycleRequest {

    private LocalDate settlementDate;
    private String currency = "LAK";

    public LocalDate getSettlementDate() { return settlementDate; }
    public void setSettlementDate(LocalDate v) { this.settlementDate = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
}
