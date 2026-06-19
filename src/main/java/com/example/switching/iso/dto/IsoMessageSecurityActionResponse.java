package com.example.switching.iso.dto;

public class IsoMessageSecurityActionResponse {

    private Long isoMessageId;
    private String transferRef;
    private String messageType;
    private String direction;
    private String previousSecurityStatus;
    private String newSecurityStatus;
    private boolean plainPayloadPresent;
    private boolean encryptedPayloadPresent;
    private String message;

    public IsoMessageSecurityActionResponse() {
    }

    public IsoMessageSecurityActionResponse(Long isoMessageId,
                                            String transferRef,
                                            String messageType,
                                            String direction,
                                            String previousSecurityStatus,
                                            String newSecurityStatus,
                                            boolean plainPayloadPresent,
                                            boolean encryptedPayloadPresent,
                                            String message) {
        this.isoMessageId = isoMessageId;
        this.transferRef = transferRef;
        this.messageType = messageType;
        this.direction = direction;
        this.previousSecurityStatus = previousSecurityStatus;
        this.newSecurityStatus = newSecurityStatus;
        this.plainPayloadPresent = plainPayloadPresent;
        this.encryptedPayloadPresent = encryptedPayloadPresent;
        this.message = message;
    }

    public Long getIsoMessageId() {
        return isoMessageId;
    }

    public void setIsoMessageId(Long isoMessageId) {
        this.isoMessageId = isoMessageId;
    }

    public String getTransferRef() {
        return transferRef;
    }

    public void setTransferRef(String transferRef) {
        this.transferRef = transferRef;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getPreviousSecurityStatus() {
        return previousSecurityStatus;
    }

    public void setPreviousSecurityStatus(String previousSecurityStatus) {
        this.previousSecurityStatus = previousSecurityStatus;
    }

    public String getNewSecurityStatus() {
        return newSecurityStatus;
    }

    public void setNewSecurityStatus(String newSecurityStatus) {
        this.newSecurityStatus = newSecurityStatus;
    }

    public boolean isPlainPayloadPresent() {
        return plainPayloadPresent;
    }

    public void setPlainPayloadPresent(boolean plainPayloadPresent) {
        this.plainPayloadPresent = plainPayloadPresent;
    }

    public boolean isEncryptedPayloadPresent() {
        return encryptedPayloadPresent;
    }

    public void setEncryptedPayloadPresent(boolean encryptedPayloadPresent) {
        this.encryptedPayloadPresent = encryptedPayloadPresent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}