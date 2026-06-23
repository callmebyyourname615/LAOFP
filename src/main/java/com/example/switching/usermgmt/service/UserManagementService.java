package com.example.switching.usermgmt.service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.switching.audit.service.AuditLogService;
import com.example.switching.participant.repository.ParticipantRepository;
import com.example.switching.usermgmt.dto.CreateUserRequest;
import com.example.switching.usermgmt.dto.UserResponse;
import com.example.switching.usermgmt.entity.RoleEntity;
import com.example.switching.usermgmt.entity.UserEntity;
import com.example.switching.usermgmt.enums.AuthSessionType;
import com.example.switching.usermgmt.enums.RoleType;
import com.example.switching.usermgmt.enums.UserStatus;
import com.example.switching.usermgmt.repository.AuthSessionRepository;
import com.example.switching.usermgmt.repository.RoleRepository;
import com.example.switching.usermgmt.repository.UserRepository;
import com.example.switching.webhook.crypto.SecretEncryptionService;

@Service
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class UserManagementService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final ParticipantRepository participants;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicy;
    private final TotpService totp;
    private final SecretEncryptionService encryption;
    private final AuditLogService audit;
    private final boolean mfaRequired;

    public UserManagementService(UserRepository users, RoleRepository roles,
            ParticipantRepository participants, AuthSessionRepository sessions,
            PasswordEncoder passwordEncoder, PasswordPolicyService passwordPolicy,
            TotpService totp, SecretEncryptionService encryption, AuditLogService audit,
            @org.springframework.beans.factory.annotation.Value("${switching.smos.mfa-required:true}") boolean mfaRequired) {
        this.users = users; this.roles = roles; this.participants = participants; this.sessions = sessions;
        this.passwordEncoder = passwordEncoder; this.passwordPolicy = passwordPolicy; this.totp = totp;
        this.encryption = encryption; this.audit = audit; this.mfaRequired = mfaRequired;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, String actor) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username)) throw new IllegalArgumentException("Username already exists");
        if (users.existsByEmailIgnoreCase(email)) throw new IllegalArgumentException("Email already exists");
        if (mfaRequired && !request.mfaEnabled()) throw new IllegalArgumentException("MFA enrollment is mandatory for SMOS users");
        passwordPolicy.validate(request.initialPassword(), username, email);
        Set<RoleEntity> assignedRoles = requireRoles(request.roles());
        boolean participantAdmin = request.roles().contains(RoleType.PARTICIPANT_ADMIN);
        if (participantAdmin && request.participantId() == null) {
            throw new IllegalArgumentException("PARTICIPANT_ADMIN requires participantId scope");
        }
        if (!participantAdmin && request.participantId() != null) {
            throw new IllegalArgumentException("participantId is only allowed for PARTICIPANT_ADMIN");
        }
        if (request.participantId() != null && !participants.existsById(request.participantId())) {
            throw new IllegalArgumentException("Participant scope does not exist");
        }

        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.setUsername(username); user.setEmail(email); user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.initialPassword()));
        user.setPasswordChangedAt(now); user.setStatus(UserStatus.ACTIVE); user.setRoles(assignedRoles);
        user.setParticipantId(request.participantId());
        String enrollmentSecret = null;
        if (request.mfaEnabled()) {
            enrollmentSecret = totp.generateSecret();
            user.setMfaSecretCiphertext(encryption.encrypt(enrollmentSecret).ciphertext());
            user.setMfaEnrolledAt(now);
        }
        UserEntity saved = users.save(user);
        audit.log("SMOS_USER_CREATED", "SMOS_USER", String.valueOf(saved.getId()), actor,
                Map.of("username", saved.getUsername(), "roles", request.roles(), "mfaEnabled", request.mfaEnabled(),
                        "participantId", request.participantId() == null ? "" : request.participantId()));
        return response(saved, enrollmentSecret);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return users.findAll(PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "username")))
                .map(user -> response(user, null));
    }
    @Transactional(readOnly = true)
    public UserResponse get(Long id) { return response(require(id), null); }

    @Transactional
    public UserResponse updateStatus(Long id, UserStatus status, String actor) {
        UserEntity user = require(id); user.setStatus(status);
        if (status != UserStatus.ACTIVE) {
            sessions.revokeAll(user.getId(), AuthSessionType.REFRESH_TOKEN, Instant.now());
            sessions.revokeAll(user.getId(), AuthSessionType.MFA_CHALLENGE, Instant.now());
        } else {
            user.setFailedLoginCount(0); user.setLockedAt(null); user.setLastFailedLoginAt(null);
        }
        audit.log("SMOS_USER_STATUS_CHANGED", "SMOS_USER", String.valueOf(id), actor,
                Map.of("username", user.getUsername(), "status", status.name()));
        return response(users.save(user), null);
    }

    @Transactional
    public UserResponse assignRoles(Long id, Set<RoleType> requestedRoles, String actor) {
        UserEntity user = require(id);
        if (requestedRoles.contains(RoleType.PARTICIPANT_ADMIN) && user.getParticipantId() == null) {
            throw new IllegalArgumentException("PARTICIPANT_ADMIN requires participantId scope");
        }
        user.setRoles(requireRoles(requestedRoles));
        sessions.revokeAll(user.getId(), AuthSessionType.REFRESH_TOKEN, Instant.now());
        audit.log("SMOS_USER_ROLES_CHANGED", "SMOS_USER", String.valueOf(id), actor,
                Map.of("username", user.getUsername(), "roles", requestedRoles));
        return response(users.save(user), null);
    }

    private UserEntity require(Long id) { return users.findById(id).orElseThrow(() -> new IllegalArgumentException("SMOS user not found: " + id)); }
    private Set<RoleEntity> requireRoles(Set<RoleType> requested) {
        List<RoleEntity> found = roles.findByNameIn(requested);
        if (found.size() != requested.size()) throw new IllegalArgumentException("One or more SMOS roles do not exist");
        return new LinkedHashSet<>(found);
    }
    private UserResponse response(UserEntity user, String enrollmentSecret) {
        Set<RoleType> roleNames = user.getRoles().stream().map(RoleEntity::getName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                user.getStatus(), user.getMfaSecretCiphertext() != null, Set.copyOf(roleNames), user.getParticipantId(),
                user.getLastLoginAt(), user.getPasswordChangedAt(), user.getLockedAt(), user.getCreatedAt(), enrollmentSecret);
    }
}
