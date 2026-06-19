package com.example.switching.iso.inbound;

import java.math.BigDecimal;

public class Pacs008InboundRequest {

    private final String messageId;
    private final String creationDateTime;
    private final String numberOfTransactions;

    private final String instructionId;
    private final String endToEndId;

    private final BigDecimal amount;
    private final String currency;

    private final String debtorAgentBic;
    private final String creditorAgentBic;

    private final String debtorAccount;
    private final String creditorAccount;

    private final String remittanceInformation;

    /*
     * ISO-INQ-2:
     * Optional inquiry reference extracted from PACS.008 SplmtryData.
     *
     * Mandatory validation is enforced later in InboundPacs008PersistenceService.
     */
    private final String inquiryRef;

    public Pacs008InboundRequest(
            String messageId,
            String creationDateTime,
            String numberOfTransactions,
            String instructionId,
            String endToEndId,
            BigDecimal amount,
            String currency,
            String debtorAgentBic,
            String creditorAgentBic,
            String debtorAccount,
            String creditorAccount,
            String remittanceInformation
    ) {
        this(
                messageId,
                creationDateTime,
                numberOfTransactions,
                instructionId,
                endToEndId,
                amount,
                currency,
                debtorAgentBic,
                creditorAgentBic,
                debtorAccount,
                creditorAccount,
                remittanceInformation,
                null
        );
    }

    public Pacs008InboundRequest(
            String messageId,
            String creationDateTime,
            String numberOfTransactions,
            String instructionId,
            String endToEndId,
            BigDecimal amount,
            String currency,
            String debtorAgentBic,
            String creditorAgentBic,
            String debtorAccount,
            String creditorAccount,
            String remittanceInformation,
            String inquiryRef
    ) {
        this.messageId = messageId;
        this.creationDateTime = creationDateTime;
        this.numberOfTransactions = numberOfTransactions;
        this.instructionId = instructionId;
        this.endToEndId = endToEndId;
        this.amount = amount;
        this.currency = currency;
        this.debtorAgentBic = debtorAgentBic;
        this.creditorAgentBic = creditorAgentBic;
        this.debtorAccount = debtorAccount;
        this.creditorAccount = creditorAccount;
        this.remittanceInformation = remittanceInformation;
        this.inquiryRef = inquiryRef;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getCreationDateTime() {
        return creationDateTime;
    }

    public String getNumberOfTransactions() {
        return numberOfTransactions;
    }

    public String getInstructionId() {
        return instructionId;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDebtorAgentBic() {
        return debtorAgentBic;
    }

    public String getCreditorAgentBic() {
        return creditorAgentBic;
    }

    public String getDebtorAccount() {
        return debtorAccount;
    }

    public String getCreditorAccount() {
        return creditorAccount;
    }

    public String getRemittanceInformation() {
        return remittanceInformation;
    }

    public String getInquiryRef() {
        return inquiryRef;
    }
}
