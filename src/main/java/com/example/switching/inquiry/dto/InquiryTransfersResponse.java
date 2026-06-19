package com.example.switching.inquiry.dto;

import java.util.List;

public class InquiryTransfersResponse {

    private String inquiryRef;
    private boolean hasTransfer;
    private int transferCount;
    private List<InquiryTransferItemResponse> items;

    public InquiryTransfersResponse() {
    }

    public InquiryTransfersResponse(String inquiryRef,
                                    boolean hasTransfer,
                                    int transferCount,
                                    List<InquiryTransferItemResponse> items) {
        this.inquiryRef = inquiryRef;
        this.hasTransfer = hasTransfer;
        this.transferCount = transferCount;
        this.items = items;
    }

    public String getInquiryRef() {
        return inquiryRef;
    }

    public void setInquiryRef(String inquiryRef) {
        this.inquiryRef = inquiryRef;
    }

    public boolean isHasTransfer() {
        return hasTransfer;
    }

    public void setHasTransfer(boolean hasTransfer) {
        this.hasTransfer = hasTransfer;
    }

    public int getTransferCount() {
        return transferCount;
    }

    public void setTransferCount(int transferCount) {
        this.transferCount = transferCount;
    }

    public List<InquiryTransferItemResponse> getItems() {
        return items;
    }

    public void setItems(List<InquiryTransferItemResponse> items) {
        this.items = items;
    }
}