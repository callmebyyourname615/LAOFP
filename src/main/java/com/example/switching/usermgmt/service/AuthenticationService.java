package com.example.switching.usermgmt.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
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
import com.example.switching.usermgmt.dto.SessionResponse;
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
    private final UserRepository users; private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords; private final TotpService totp;
    private final SecretEncryptionService encryption; private final SmosTokenService tokens;
    private final AuthorizationService authorization; private final AuditLogService audit;
    private final SecureRandom random = new SecureRandom(); private final int maxFailedLogins;
    private final long mfaTtlSeconds; private final long refreshTtlSeconds; private final boolean mfaRequired;

    public AuthenticationService(UserRepository users, AuthSessionRepository sessions,
            PasswordEncoder passwords, TotpService totp, SecretEncryptionService encryption,
            SmosTokenService tokens, AuthorizationService authorization, AuditLogService audit,
            @Value("${switching.smos.max-failed-logins:5}") int maxFailedLogins,
            @Value("${switching.smos.mfa-challenge-ttl-seconds:60}") long mfaTtlSeconds,
            @Value("${switching.smos.refresh-token-ttl-seconds:28800}") long refreshTtlSeconds,
            @Value("${switching.smos.mfa-required:true}") boolean mfaRequired) {
        this.users=users; this.sessions=sessions; this.passwords=passwords; this.totp=totp;
        this.encryption=encryption; this.tokens=tokens; this.authorization=authorization; this.audit=audit;
        this.maxFailedLogins=maxFailedLogins; this.mfaTtlSeconds=mfaTtlSeconds;
        this.refreshTtlSeconds=refreshTtlSeconds; this.mfaRequired=mfaRequired;
    }

    @Transactional(noRollbackFor = SmosAuthenticationException.class)
    public AuthResponse login(String username, String password) {
        UserEntity user=users.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new SmosAuthenticationException("Invalid username or password"));
        if (user.getStatus()!=UserStatus.ACTIVE) throw new SmosAuthenticationException("User account is not active");
        if (!passwords.matches(password,user.getPasswordHash())) {
            recordFailure(user,"SMOS_LOGIN_FAILED");
            throw new SmosAuthenticationException("Invalid username or password");
        }
        clearFailures(user);
        if (mfaRequired && (user.getMfaSecretCiphertext()==null || user.getMfaSecretCiphertext().isBlank())) {
            audit.log("SMOS_MFA_ENROLLMENT_REQUIRED","SMOS_USER",String.valueOf(user.getId()),user.getUsername(),Map.of());
            throw new SmosAuthenticationException("MFA enrollment is required for this account");
        }
        if (user.getMfaSecretCiphertext()!=null && !user.getMfaSecretCiphertext().isBlank()) {
            return AuthResponse.mfa(createSession(user,AuthSessionType.MFA_CHALLENGE,mfaTtlSeconds,null,null),mfaTtlSeconds);
        }
        return issueTokens(user,UUID.randomUUID(),null);
    }

    @Transactional(noRollbackFor = SmosAuthenticationException.class)
    public AuthResponse verifyMfa(String challenge,String code) {
        AuthSessionEntity session=sessions.findActive(TokenHashing.sha256(challenge),AuthSessionType.MFA_CHALLENGE,Instant.now())
                .orElseThrow(() -> new SmosAuthenticationException("MFA challenge is invalid or expired"));
        UserEntity user=users.findByUsernameIgnoreCase(session.getUser().getUsername())
                .orElseThrow(() -> new SmosAuthenticationException("User account not found"));
        if (user.getStatus()!=UserStatus.ACTIVE) { revoke(session); throw new SmosAuthenticationException("User account is not active"); }
        if (!totp.verify(encryption.decrypt(user.getMfaSecretCiphertext()),code)) {
            recordFailure(user,"SMOS_MFA_FAILED");
            if (user.getStatus()==UserStatus.LOCKED) revoke(session);
            throw new SmosAuthenticationException("Invalid MFA code");
        }
        revoke(session); clearFailures(user);
        return issueTokens(user,UUID.randomUUID(),null);
    }

    @Transactional(noRollbackFor = SmosAuthenticationException.class)
    public AuthResponse refresh(String refreshToken) {
        String hash=TokenHashing.sha256(refreshToken); Instant now=Instant.now();
        AuthSessionEntity session=sessions.findByTokenHashAndType(hash,AuthSessionType.REFRESH_TOKEN)
                .orElseThrow(() -> new SmosAuthenticationException("Refresh token is invalid or expired"));
        if (session.getRevokedAt()!=null || !session.getExpiresAt().isAfter(now)) {
            if (session.getRevokedAt()!=null) {
                sessions.revokeFamily(session.getSessionFamilyId(),now);
                audit.log("SMOS_REFRESH_REUSE_DETECTED","SMOS_USER",String.valueOf(session.getUser().getId()),
                        session.getUser().getUsername(),Map.of("sessionFamilyId",session.getSessionFamilyId().toString()));
            }
            throw new SmosAuthenticationException("Refresh token is invalid or expired");
        }
        session.setLastUsedAt(now); revoke(session);
        UserEntity user=users.findByUsernameIgnoreCase(session.getUser().getUsername())
                .orElseThrow(() -> new SmosAuthenticationException("User account not found"));
        if (user.getStatus()!=UserStatus.ACTIVE) throw new SmosAuthenticationException("User account is not active");
        return issueTokens(user,session.getSessionFamilyId(),session.getId());
    }

    @Transactional public void logout(String refreshToken) {
        sessions.findActive(TokenHashing.sha256(refreshToken),AuthSessionType.REFRESH_TOKEN,Instant.now()).ifPresent(this::revoke);
    }
    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(String username) {
        UserEntity user=requireActive(username);
        return sessions.findActiveByUser(user.getId(),AuthSessionType.REFRESH_TOKEN,Instant.now()).stream()
                .map(s -> new SessionResponse(s.getId(),s.getSessionFamilyId(),s.getCreatedAt(),s.getLastUsedAt(),s.getExpiresAt())).toList();
    }
    @Transactional public void revokeSession(String username,UUID sessionId) {
        UserEntity user=requireActive(username); AuthSessionEntity session=sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getUser().getId().equals(user.getId())) throw new IllegalArgumentException("Session not found");
        sessions.revokeFamily(session.getSessionFamilyId(),Instant.now());
        audit.log("SMOS_SESSION_REVOKED","SMOS_USER",String.valueOf(user.getId()),username,Map.of("sessionId",sessionId.toString()));
    }
    @Transactional public int revokeAllSessions(String username) {
        UserEntity user=requireActive(username); int count=sessions.revokeAll(user.getId(),AuthSessionType.REFRESH_TOKEN,Instant.now());
        audit.log("SMOS_ALL_SESSIONS_REVOKED","SMOS_USER",String.valueOf(user.getId()),username,Map.of("revoked",count));
        return count;
    }

    private AuthResponse issueTokens(UserEntity user,UUID familyId,UUID rotatedFromId) {
        Set<String> roles=authorization.roles(user), permissions=authorization.permissions(user);
        String accessToken=tokens.issue(user,roles,permissions);
        String refreshToken=createSession(user,AuthSessionType.REFRESH_TOKEN,refreshTtlSeconds,familyId,rotatedFromId);
        user.setLastLoginAt(Instant.now()); clearFailures(user);
        audit.log("SMOS_LOGIN_SUCCEEDED","SMOS_USER",String.valueOf(user.getId()),user.getUsername(),Map.of("roles",roles));
        return new AuthResponse(false,null,accessToken,refreshToken,tokens.ttlSeconds(),roles,permissions);
    }
    private String createSession(UserEntity user,AuthSessionType type,long ttl,UUID familyId,UUID rotatedFromId) {
        byte[] bytes=new byte[32]; random.nextBytes(bytes); String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id=UUID.randomUUID(); AuthSessionEntity session=new AuthSessionEntity();
        session.setId(id); session.setUser(user); session.setSessionType(type); session.setTokenHash(TokenHashing.sha256(raw));
        session.setExpiresAt(Instant.now().plusSeconds(ttl)); session.setSessionFamilyId(familyId==null?id:familyId); session.setRotatedFromId(rotatedFromId);
        sessions.save(session); return raw;
    }
    private void recordFailure(UserEntity user,String event) {
        int failures=user.getFailedLoginCount()+1; Instant now=Instant.now(); user.setFailedLoginCount(failures); user.setLastFailedLoginAt(now);
        if (failures>=maxFailedLogins) { user.setStatus(UserStatus.LOCKED); user.setLockedAt(now); }
        users.save(user); audit.log(event,"SMOS_USER",String.valueOf(user.getId()),user.getUsername(),Map.of("failedLoginCount",failures));
    }
    private void clearFailures(UserEntity user) { user.setFailedLoginCount(0); user.setLastFailedLoginAt(null); user.setLockedAt(null); users.save(user); }
    private void revoke(AuthSessionEntity session) { session.setRevokedAt(Instant.now()); sessions.save(session); }
    private UserEntity requireActive(String username) {
        UserEntity user=users.findByUsernameIgnoreCase(username).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getStatus()!=UserStatus.ACTIVE) throw new SmosAuthenticationException("User account is not active");
        return user;
    }
}
