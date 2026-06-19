package com.example.switching.iso.dto;

public class IsoMessageSecurityPolicyResponse {

    private Long id;
    private String messageId;
    private String transferRef;
    private String messageType;
    private String direction;
    private String currentSecurityStatus;
    private String requiredSecurityStatus;
    private boolean compliant;
    private boolean encryptedPayloadRequired;
    private boolean plainPayloadAllowed;
    private boolean validationAllowed;
    private String policyCode;
    private String policyMessage;

    public IsoMessageSecurityPolicyResponse() {
    }

    public Long getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getTransferRef() {
        return transferRef;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getDirection() {
        return direction;
    }

    public String getCurrentSecurityStatus() {
        return currentSecurityStatus;
    }

    public String getRequiredSecurityStatus() {
        return requiredSecurityStatus;
    }

    public boolean isCompliant() {
        return compliant;
    }

    public boolean isEncryptedPayloadRequired() {
        return encryptedPayloadRequired;
    }

    public boolean isPlainPayloadAllowed() {
        return plainPayloadAllowed;
    }

    public boolean isValidationAllowed() {
        return validationAllowed;
    }

    public String getPolicyCode() {
        return policyCode;
    }

    public String getPolicyMessage() {
        return policyMessage;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public void setTransferRef(String transferRef) {
        this.transferRef = transferRef;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setCurrentSecurityStatus(String currentSecurityStatus) {
        this.currentSecurityStatus = currentSecurityStatus;
    }

    public void setRequiredSecurityStatus(String requiredSecurityStatus) {
        this.requiredSecurityStatus = requiredSecurityStatus;
    }

    public void setCompliant(boolean compliant) {
        this.compliant = compliant;
    }

    public void setEncryptedPayloadRequired(boolean encryptedPayloadRequired) {
        this.encryptedPayloadRequired = encryptedPayloadRequired;
    }

    public void setPlainPayloadAllowed(boolean plainPayloadAllowed) {
        this.plainPayloadAllowed = plainPayloadAllowed;
    }

    public void setValidationAllowed(boolean validationAllowed) {
        this.validationAllowed = validationAllowed;
    }

    public void setPolicyCode(String policyCode) {
        this.policyCode = policyCode;
    }

    public void setPolicyMessage(String policyMessage) {
        this.policyMessage = policyMessage;
    }
}