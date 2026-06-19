package com.example.switching.iso.dto;

import java.util.List;

public class IsoMessageListResponse {

    private int count;
    private int limit;

    private String messageType;
    private String direction;
    private String correlationRef;
    private String inquiryRef;
    private String transferRef;
    private String endToEndId;

    private List<IsoMessageItemResponse> items;

    public IsoMessageListResponse() {
    }

    public IsoMessageListResponse(int count,
                                  int limit,
                                  String messageType,
                                  String direction,
                                  String correlationRef,
                                  String inquiryRef,
                                  String transferRef,
                                  String endToEndId,
                                  List<IsoMessageItemResponse> items) {
        this.count = count;
        this.limit = limit;
        this.messageType = messageType;
        this.direction = direction;
        this.correlationRef = correlationRef;
        this.inquiryRef = inquiryRef;
        this.transferRef = transferRef;
        this.endToEndId = endToEndId;
        this.items = items;
    }

    public int getCount() {
        return count;
    }

    public int getLimit() {
        return limit;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getDirection() {
        return direction;
    }

    public String getCorrelationRef() {
        return correlationRef;
    }

    public String getInquiryRef() {
        return inquiryRef;
    }

    public String getTransferRef() {
        return transferRef;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public List<IsoMessageItemResponse> getItems() {
        return items;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setCorrelationRef(String correlationRef) {
        this.correlationRef = correlationRef;
    }

    public void setInquiryRef(String inquiryRef) {
        this.inquiryRef = inquiryRef;
    }

    public void setTransferRef(String transferRef) {
        this.transferRef = transferRef;
    }

    public void setEndToEndId(String endToEndId) {
        this.endToEndId = endToEndId;
    }

    public void setItems(List<IsoMessageItemResponse> items) {
        this.items = items;
    }
}