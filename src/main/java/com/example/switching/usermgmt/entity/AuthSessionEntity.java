package com.example.switching.usermgmt.entity;

import java.time.Instant;
import java.util.UUID;
import com.example.switching.usermgmt.enums.AuthSessionType;
import jakarta.persistence.*;

@Entity
@Table(name = "smos_auth_sessions")
public class AuthSessionEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Enumerated(EnumType.STRING) @Column(name = "session_type", nullable = false, length = 24)
    private AuthSessionType sessionType;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "session_family_id", nullable = false) private UUID sessionFamilyId;
    @Column(name = "rotated_from_id") private UUID rotatedFromId;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(name = "client_fingerprint_hash", length = 64) private String clientFingerprintHash;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }
    public AuthSessionType getSessionType() { return sessionType; }
    public void setSessionType(AuthSessionType sessionType) { this.sessionType = sessionType; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getSessionFamilyId() { return sessionFamilyId; }
    public void setSessionFamilyId(UUID value) { this.sessionFamilyId = value; }
    public UUID getRotatedFromId() { return rotatedFromId; }
    public void setRotatedFromId(UUID value) { this.rotatedFromId = value; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant value) { this.lastUsedAt = value; }
    public String getClientFingerprintHash() { return clientFingerprintHash; }
    public void setClientFingerprintHash(String value) { this.clientFingerprintHash = value; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
