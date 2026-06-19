package com.example.switching.transfer.dto;

import java.util.List;

public class TransferListResponse {

    private int count;
    private int limit;
    private String status;
    private String inquiryRef;
    private String sourceBank;
    private String destinationBank;
    private List<TransferListItemResponse> items;

    public TransferListResponse() {
    }

    public TransferListResponse(int count,
                                int limit,
                                String status,
                                String inquiryRef,
                                String sourceBank,
                                String destinationBank,
                                List<TransferListItemResponse> items) {
        this.count = count;
        this.limit = limit;
        this.status = status;
        this.inquiryRef = inquiryRef;
        this.sourceBank = sourceBank;
        this.destinationBank = destinationBank;
        this.items = items;
    }

    public int getCount() {
        return count;
    }

    public int getLimit() {
        return limit;
    }

    public String getStatus() {
        return status;
    }

    public String getInquiryRef() {
        return inquiryRef;
    }

    public String getSourceBank() {
        return sourceBank;
    }

    public String getDestinationBank() {
        return destinationBank;
    }

    public List<TransferListItemResponse> getItems() {
        return items;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setInquiryRef(String inquiryRef) {
        this.inquiryRef = inquiryRef;
    }

    public void setSourceBank(String sourceBank) {
        this.sourceBank = sourceBank;
    }

    public void setDestinationBank(String destinationBank) {
        this.destinationBank = destinationBank;
    }

    public void setItems(List<TransferListItemResponse> items) {
        this.items = items;
    }
}