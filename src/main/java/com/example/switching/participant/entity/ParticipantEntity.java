package com.example.switching.participant.entity;

import java.time.LocalDateTime;

import com.example.switching.participant.enums.ParticipantStatus;
import com.example.switching.participant.enums.ParticipantType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "participants")
public class ParticipantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_code", nullable = false, unique = true, length = 32)
    private String bankCode;

    @Column(name = "bank_name", nullable = false, length = 255)
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ParticipantStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, length = 32)
    private ParticipantType participantType;

    @Column(name = "country", nullable = false, length = 8)
    private String country;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ParticipantEntity() {
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();

        if (this.status == null) {
            this.status = ParticipantStatus.ACTIVE;
        }

        if (this.participantType == null) {
            this.participantType = ParticipantType.DIRECT;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean active() {
        return ParticipantStatus.ACTIVE == status;
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

    public ParticipantStatus getStatus() {
        return status;
    }

    public ParticipantType getParticipantType() {
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

    public void setStatus(ParticipantStatus status) {
        this.status = status;
    }

    public void setParticipantType(ParticipantType participantType) {
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