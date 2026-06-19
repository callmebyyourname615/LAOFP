package com.example.switching.reconciliation.dto;

import java.math.BigDecimal;

/** One line item submitted as part of a reconciliation batch. */
public class ReconItemRequest {

    private int lineNumber;
    private String transactionRef;
    private String externalRef;
    private BigDecimal amount;
    private String currency;

    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
