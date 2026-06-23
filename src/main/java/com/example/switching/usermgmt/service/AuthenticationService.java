package com.example.switching.usermgmt.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.switching.audit.service.AuditLogService;
import com.example.switching.usermgmt.dto.AuthResponse;
import com.example.switching.usermgmt.entity.AuthSessionEntity;
import com.example.switching.usermgmt.entity.UserEntity;
import com.example.switching.usermgmt.enums.AuthSessionType;
import com.example.switching.usermgmt.enums.UserStatus;
import com.example.switching.usermgmt.repository.AuthSessionRepository;
import com.example.switching.usermgmt.repository.UserRepository;
import com.example.switching.webhook.crypto.SecretEncryptionService;

@Service
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class AuthenticationService {
    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final TotpService totp;
    private final SecretEncryptionService encryption;
    private final SmosTokenService tokens;
    private final AuthorizationService authorization;
    private final AuditLogService audit;
    private final SecureRandom random = new SecureRandom();
    private final int maxFailedLogins;
    private final long mfaTtlSeconds;
    private final long refreshTtlSeconds;
    private final boolean mfaRequired;

    public AuthenticationService(UserRepository users, AuthSessionRepository sessions,
            PasswordEncoder passwords, TotpService totp, SecretEncryptionService encryption,
            SmosTokenService tokens, AuthorizationService authorization, AuditLogService audit,
            @Value("${switching.smos.max-failed-logins:5}") int maxFailedLogins,
            @Value("${switching.smos.mfa-challenge-ttl-seconds:60}") long mfaTtlSeconds,
            @Value("${switching.smos.refresh-token-ttl-seconds:28800}") long refreshTtlSeconds,
            @Value("${switching.smos.mfa-required:true}") boolean mfaRequired) {
        this.users = users; this.sessions = sessions; this.passwords = passwords;
        this.totp = totp; this.encryption = encryption; this.tokens = tokens;
        this.authorization = authorization; this.audit = audit;
        this.maxFailedLogins = maxFailedLogins; this.mfaTtlSeconds = mfaTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds; this.mfaRequired = mfaRequired;
    }

    @Transactional(noRollbackFor = SmosAuthenticationException.class)
    public AuthResponse login(String username, String password) {
        UserEntity user = users.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new SmosAuthenticationException("Invalid username or password"));
        if (user.getStatus() != UserStatus.ACTIVE) throw new SmosAuthenticationException("User account is not active");
        if (!passwords.matches(password, user.getPasswordHash())) {
            int failures = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(failures);
            if (failures >= maxFailedLogins) user.setStatus(UserStatus.LOCKED);
            users.save(user);
            audit.log("SMOS_LOGIN_FAILED", "SMOS_USER", String.valueOf(user.getId()), user.getUsername(),
                    Map.of("failedLoginCount", failures));
            throw new SmosAuthenticationException("Invalid username or password");
        }
        user.setFailedLoginCount(0);
        users.save(user);
        if (mfaRequired && (user.getMfaSecretCiphertext() == null || user.getMfaSecretCiphertext().isBlank())) {
            audit.log("SMOS_MFA_ENROLLMENT_REQUIRED", "SMOS_USER", String.valueOf(user.getId()), user.getUsername(), Map.of());
            throw new SmosAuthenticationException("MFA enrollment is required for this account");
        }
        if (user.getMfaSecretCiphertext() != null && !user.getMfaSecretCiphertext().isBlank()) {
            String challenge = createSession(user, AuthSessionType.MFA_CHALLENGE, mfaTtlSeconds);
            return AuthResponse.mfa(challenge, mfaTtlSeconds);
        }
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = SmosAuthenticationException.class)
    public AuthResponse verifyMfa(String challenge, String code) {
        AuthSessionEntity session = sessions.findActive(TokenHashing.sha256(challenge), AuthSessionType.MFA_CHALLENGE, Instant.now())
                .orElseThrow(() -> new SmosAuthenticationException("MFA challenge is invalid or expired"));
        UserEntity user = users.findByUsernameIgnoreCase(session.getUser().getUsername())
                .orElseThrow(() -> new SmosAuthenticationException("User account not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            session.setRevokedAt(Instant.now());
            sessions.save(session);
            throw new SmosAuthenticationException("User account is not active");
        }
        String secret = encryption.decrypt(user.getMfaSecretCiphertext());
        if (!totp.verify(secret, code)) {
            int failures = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(failures);
            if (failures >= maxFailedLogins) {
                user.setStatus(UserStatus.LOCKED);
                session.setRevokedAt(Instant.now());
                sessions.save(session);
            }
            users.save(user);
            audit.log("SMOS_MFA_FAILED", "SMOS_USER", String.valueOf(user.getId()), user.getUsername(),
                    Map.of("failedLoginCount", failures));
            throw new SmosAuthenticationException("Invalid MFA code");
        }
        session.setRevokedAt(Instant.now());
        sessions.save(session);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        AuthSessionEntity session = sessions.findActive(TokenHashing.sha256(refreshToken), AuthSessionType.REFRESH_TOKEN, Instant.now())
                .orElseThrow(() -> new SmosAuthenticationException("Refresh token is invalid or expired"));
        session.setRevokedAt(Instant.now());
        sessions.save(session);
        UserEntity user = users.findByUsernameIgnoreCase(session.getUser().getUsername())
                .orElseThrow(() -> new SmosAuthenticationException("User account not found"));
        if (user.getStatus() != UserStatus.ACTIVE) throw new SmosAuthenticationException("User account is not active");
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        sessions.findActive(TokenHashing.sha256(refreshToken), AuthSessionType.REFRESH_TOKEN, Instant.now())
                .ifPresent(session -> { session.setRevokedAt(Instant.now()); sessions.save(session); });
    }

    private AuthResponse issueTokens(UserEntity user) {
        Set<String> roles = authorization.roles(user);
        Set<String> permissions = authorization.permissions(user);
        String accessToken = tokens.issue(user, roles, permissions);
        String refreshToken = createSession(user, AuthSessionType.REFRESH_TOKEN, refreshTtlSeconds);
        user.setLastLoginAt(Instant.now());
        user.setFailedLoginCount(0);
        users.save(user);
        audit.log("SMOS_LOGIN_SUCCEEDED", "SMOS_USER", String.valueOf(user.getId()), user.getUsername(),
                Map.of("roles", roles));
        return new AuthResponse(false, null, accessToken, refreshToken, tokens.ttlSeconds(), roles, permissions);
    }

    private String createSession(UserEntity user, AuthSessionType type, long ttl) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AuthSessionEntity session = new AuthSessionEntity();
        session.setId(UUID.randomUUID()); session.setUser(user); session.setSessionType(type);
        session.setTokenHash(TokenHashing.sha256(raw)); session.setExpiresAt(Instant.now().plusSeconds(ttl));
        sessions.save(session);
        return raw;
    }
}
