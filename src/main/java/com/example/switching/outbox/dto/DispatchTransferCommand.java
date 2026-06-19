package com.example.switching.outbox.dto;

import java.math.BigDecimal;

public class DispatchTransferCommand {

    private String transferRef;
    private String sourceBank;
    private String debtorAccount;
    private String destinationBank;
    private String creditorAccount;
    private BigDecimal amount;
    private String currency;
    private String connectorName;
    private String routeCode;
    private Long isoMessageId;

    public Long getIsoMessageId() {
        return isoMessageId;
    }

    public void setIsoMessageId(Long isoMessageId) {
        this.isoMessageId = isoMessageId;
    }

    public DispatchTransferCommand() {
    }

    public DispatchTransferCommand(String transferRef,
                               Long isoMessageId,
                               String sourceBank,
                               String debtorAccount,
                               String destinationBank,
                               String creditorAccount,
                               BigDecimal amount,
                               String currency,
                               String connectorName,
                               String routeCode) {
    this.transferRef = transferRef;
    this.isoMessageId = isoMessageId;
    this.sourceBank = sourceBank;
    this.debtorAccount = debtorAccount;
    this.destinationBank = destinationBank;
    this.creditorAccount = creditorAccount;
    this.amount = amount;
    this.currency = currency;
    this.connectorName = connectorName;
    this.routeCode = routeCode;
}

    public String getTransferRef() {
        return transferRef;
    }

    public void setTransferRef(String transferRef) {
        this.transferRef = transferRef;
    }

    public String getSourceBank() {
        return sourceBank;
    }

    public void setSourceBank(String sourceBank) {
        this.sourceBank = sourceBank;
    }

    public String getDebtorAccount() {
        return debtorAccount;
    }

    public void setDebtorAccount(String debtorAccount) {
        this.debtorAccount = debtorAccount;
    }

    public String getDestinationBank() {
        return destinationBank;
    }

    public void setDestinationBank(String destinationBank) {
        this.destinationBank = destinationBank;
    }

    public String getCreditorAccount() {
        return creditorAccount;
    }

    public void setCreditorAccount(String creditorAccount) {
        this.creditorAccount = creditorAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public void setConnectorName(String connectorName) {
        this.connectorName = connectorName;
    }

    public String getRouteCode() {
        return routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }
}