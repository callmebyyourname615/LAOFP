package com.example.switching.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class ApiKeyCreateRequest {

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotNull
    private String role; // ADMIN | OPS | BANK

    private String bankCode; // required when role=BANK

    private LocalDateTime expiresAt; // null = no expiry

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getBankCode() { return bankCode; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
