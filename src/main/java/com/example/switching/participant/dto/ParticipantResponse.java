package com.example.switching.participant.dto;

import java.time.LocalDateTime;

import com.example.switching.participant.entity.ParticipantEntity;

public class ParticipantResponse {

    private Long id;
    private String bankCode;
    private String bankName;
    private String status;
    private String participantType;
    private String country;
    private String currency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ParticipantResponse() {
    }

    public static ParticipantResponse from(ParticipantEntity entity) {
        ParticipantResponse response = new ParticipantResponse();

        response.setId(entity.getId());
        response.setBankCode(entity.getBankCode());
        response.setBankName(entity.getBankName());
        response.setStatus(entity.getStatus() == null ? null : entity.getStatus().name());
        response.setParticipantType(entity.getParticipantType() == null ? null : entity.getParticipantType().name());
        response.setCountry(entity.getCountry());
        response.setCurrency(entity.getCurrency());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());

        return response;
    }

    public Long getId() {
        return id;
    }

    public String getBankCode() {
        return bankCode;
    }

    public String getBankName() {
        return bankName;
    }

    public String getStatus() {
        return status;
    }

    public String getParticipantType() {
        return participantType;
    }

    public String getCountry() {
        return country;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setBankCode(String bankCode) {
        this.bankCode = bankCode;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setParticipantType(String participantType) {
        this.participantType = participantType;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}