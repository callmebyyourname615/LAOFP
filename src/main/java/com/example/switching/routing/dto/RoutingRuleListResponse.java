package com.example.switching.routing.dto;

import java.util.List;

public class RoutingRuleListResponse {

    private int count;
    private List<RoutingRuleResponse> items;

    public RoutingRuleListResponse() {
    }

    public RoutingRuleListResponse(List<RoutingRuleResponse> items) {
        this.items = items;
        this.count = items == null ? 0 : items.size();
    }

    public int getCount() {
        return count;
    }

    public List<RoutingRuleResponse> getItems() {
        return items;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setItems(List<RoutingRuleResponse> items) {
        this.items = items;
    }
}