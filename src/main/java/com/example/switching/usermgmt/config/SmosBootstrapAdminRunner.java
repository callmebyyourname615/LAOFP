package com.example.switching.usermgmt.config;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.example.switching.audit.service.AuditLogService;
import com.example.switching.usermgmt.entity.RoleEntity;
import com.example.switching.usermgmt.entity.UserEntity;
import com.example.switching.usermgmt.enums.RoleType;
import com.example.switching.usermgmt.enums.UserStatus;
import com.example.switching.usermgmt.repository.RoleRepository;
import com.example.switching.usermgmt.repository.UserRepository;
import com.example.switching.webhook.crypto.SecretEncryptionService;

@Component
@ConditionalOnProperty(name = {"switching.smos.enabled", "switching.smos.bootstrap.enabled"}, havingValue = "true")
public class SmosBootstrapAdminRunner implements ApplicationRunner {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final com.example.switching.usermgmt.service.PasswordPolicyService passwordPolicy;
    private final SecretEncryptionService encryption;
    private final AuditLogService audit;
    private final String username;
    private final String email;
    private final String fullName;
    private final String password;
    private final String mfaSecret;
    private final boolean oneTimeAcknowledged;

    public SmosBootstrapAdminRunner(UserRepository users, RoleRepository roles, PasswordEncoder passwords,
            com.example.switching.usermgmt.service.PasswordPolicyService passwordPolicy,
            SecretEncryptionService encryption, AuditLogService audit,
            @Value("${switching.smos.bootstrap.username:}") String username,
            @Value("${switching.smos.bootstrap.email:}") String email,
            @Value("${switching.smos.bootstrap.full-name:Initial System Administrator}") String fullName,
            @Value("${switching.smos.bootstrap.password:}") String password,
            @Value("${switching.smos.bootstrap.mfa-secret:}") String mfaSecret,
            @Value("${switching.smos.bootstrap.acknowledge-one-time:false}") boolean oneTimeAcknowledged) {
        this.users = users; this.roles = roles; this.passwords = passwords; this.passwordPolicy = passwordPolicy; this.encryption = encryption;
        this.audit = audit; this.username = username; this.email = email; this.fullName = fullName;
        this.password = password; this.mfaSecret = mfaSecret; this.oneTimeAcknowledged = oneTimeAcknowledged;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        if (!oneTimeAcknowledged) {
            throw new IllegalStateException("SMOS bootstrap requires explicit one-time acknowledgement");
        }
        if (username.isBlank() || email.isBlank() || password.length() < 16 || mfaSecret.length() < 16) {
            throw new IllegalStateException("SMOS bootstrap requires username, email, 16+ character password and TOTP secret");
        }
        passwordPolicy.validate(password, username, email);
        RoleEntity adminRole = roles.findByName(RoleType.SYSTEM_ADMIN)
                .orElseThrow(() -> new IllegalStateException("SYSTEM_ADMIN role seed is missing"));
        UserEntity user = new UserEntity();
        user.setUsername(username.trim().toLowerCase()); user.setEmail(email.trim().toLowerCase());
        user.setFullName(fullName.trim()); user.setPasswordHash(passwords.encode(password));
        user.setStatus(UserStatus.ACTIVE); user.setRoles(Set.of(adminRole));
        user.setPasswordChangedAt(Instant.now()); user.setMfaEnrolledAt(Instant.now());
        user.setMfaSecretCiphertext(encryption.encrypt(mfaSecret).ciphertext());
        UserEntity saved = users.save(user);
        audit.log("SMOS_BOOTSTRAP_ADMIN_CREATED", "SMOS_USER", String.valueOf(saved.getId()), "SYSTEM_BOOTSTRAP",
                Map.of("username", saved.getUsername()));
    }
}
