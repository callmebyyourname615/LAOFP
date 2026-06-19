package com.example.switching.outbox.dto;

import java.util.List;

public class OutboxEventListResponse {

    private int count;
    private int limit;
    private String status;
    private String transferRef;
    private List<OutboxEventItemResponse> items;

    public OutboxEventListResponse() {
    }

    public OutboxEventListResponse(int count,
                                   int limit,
                                   String status,
                                   String transferRef,
                                   List<OutboxEventItemResponse> items) {
        this.count = count;
        this.limit = limit;
        this.status = status;
        this.transferRef = transferRef;
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

    public String getTransferRef() {
        return transferRef;
    }

    public List<OutboxEventItemResponse> getItems() {
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

    public void setTransferRef(String transferRef) {
        this.transferRef = transferRef;
    }

    public void setItems(List<OutboxEventItemResponse> items) {
        this.items = items;
    }
}