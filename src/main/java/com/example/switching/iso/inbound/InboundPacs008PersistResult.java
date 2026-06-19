package com.example.switching.iso.inbound;

public class InboundPacs008PersistResult {

    private final String transferRef;

    public InboundPacs008PersistResult(String transferRef) {
        this.transferRef = transferRef;
    }

    public String getTransferRef() {
        return transferRef;
    }
}