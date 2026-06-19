package com.example.switching.security.oauth.entity;

import java.time.LocalDateTime;

import com.example.switching.security.oauth.enums.OAuthClientStatus;
import com.example.switching.security.oauth.enums.OAuthClientTier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "oauth_clients")
@Getter
@Setter
public class OAuthClientEntity {

    @Id
    @Column(name = "client_id", nullable = false, length = 128)
    private String clientId;

    @Column(name = "psp_id", nullable = false, length = 32)
    private String pspId;

    @Column(name = "client_secret_hash", nullable = false, length = 64)
    private String clientSecretHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 16)
    private OAuthClientTier tier;

    @Column(name = "scopes", nullable = false, columnDefinition = "TEXT")
    private String scopes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OAuthClientStatus status;
}
