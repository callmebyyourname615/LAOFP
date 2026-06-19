package com.example.switching.dashboard.dto;

public class StatusCountResponse {

    private String status;
    private long count;

    public StatusCountResponse() {
    }

    public StatusCountResponse(String status, long count) {
        this.status = status;
        this.count = count;
    }

    public String getStatus() {
        return status;
    }

    public long getCount() {
        return count;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCount(long count) {
        this.count = count;
    }
}