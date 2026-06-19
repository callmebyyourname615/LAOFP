package com.example.switching.security.enums;

public enum ApiKeyRole {
    /** Full access — all endpoints */
    ADMIN,
    /** Operations / admin endpoints (/api/operations/*) + read-only on others */
    OPS,
    /** Payment endpoints — inquiries, transfers, ISO 20022 inbound */
    BANK
}
