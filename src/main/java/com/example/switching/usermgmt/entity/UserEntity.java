package com.example.switching.usermgmt.entity;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import com.example.switching.usermgmt.enums.UserStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "smos_users")
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64) private String username;
    @Column(name = "password_hash", nullable = false, length = 128) private String passwordHash;
    @Column(nullable = false, length = 160) private String email;
    @Column(name = "full_name", nullable = false, length = 160) private String fullName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private UserStatus status = UserStatus.ACTIVE;
    @Column(name = "mfa_secret_ciphertext", columnDefinition = "text") private String mfaSecretCiphertext;
    @Column(name = "failed_login_count", nullable = false) private int failedLoginCount;
    @Column(name = "last_login_at") private Instant lastLoginAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "smos_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleEntity> roles = new LinkedHashSet<>();
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public String getMfaSecretCiphertext() { return mfaSecretCiphertext; }
    public void setMfaSecretCiphertext(String value) { this.mfaSecretCiphertext = value; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public void setFailedLoginCount(int value) { this.failedLoginCount = value; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant value) { this.lastLoginAt = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<RoleEntity> getRoles() { return roles; }
    public void setRoles(Set<RoleEntity> roles) { this.roles = new LinkedHashSet<>(roles); }
}
