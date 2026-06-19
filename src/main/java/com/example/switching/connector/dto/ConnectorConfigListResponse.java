package com.example.switching.connector.dto;

import java.util.List;

public class ConnectorConfigListResponse {

    private int count;
    private List<ConnectorConfigResponse> items;

    public ConnectorConfigListResponse() {
    }

    public ConnectorConfigListResponse(List<ConnectorConfigResponse> items) {
        this.items = items;
        this.count = items == null ? 0 : items.size();
    }

    public int getCount() {
        return count;
    }

    public List<ConnectorConfigResponse> getItems() {
        return items;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setItems(List<ConnectorConfigResponse> items) {
        this.items = items;
    }
}