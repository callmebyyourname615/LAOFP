package com.example.switching.security.entity;

import java.time.LocalDateTime;

import com.example.switching.security.enums.ApiKeyRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "api_keys")
@Getter
@Setter
public class ApiKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 hex digest of the original API key. Never stores plaintext after V17. */
    @Column(name = "key_value", nullable = false, unique = true, length = 64)
    private String keyValue;

    /** First 12 characters of the original key, for display only (e.g. "sk-admin-swi"). */
    @Column(name = "key_prefix", length = 16)
    private String keyPrefix;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private ApiKeyRole role;

    @Column(name = "bank_code", length = 32)
    private String bankCode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /** NULL = key never expires. Enforced in ApiKeyAuthFilter. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
