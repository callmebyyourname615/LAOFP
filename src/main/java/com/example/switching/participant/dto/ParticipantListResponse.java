package com.example.switching.participant.dto;

import java.util.List;

public class ParticipantListResponse {

    private int count;
    private List<ParticipantResponse> items;

    public ParticipantListResponse() {
    }

    public ParticipantListResponse(List<ParticipantResponse> items) {
        this.items = items;
        this.count = items == null ? 0 : items.size();
    }

    public int getCount() {
        return count;
    }

    public List<ParticipantResponse> getItems() {
        return items;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setItems(List<ParticipantResponse> items) {
        this.items = items;
    }
}