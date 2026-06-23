package com.example.switching.usermgmt.service;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class PasswordPolicyService {
    private final int minimumLength;

    public PasswordPolicyService(@Value("${switching.smos.password-policy.minimum-length:14}") int minimumLength) {
        if (minimumLength < 12 || minimumLength > 128) {
            throw new IllegalStateException("SMOS password minimum length must be between 12 and 128");
        }
        this.minimumLength = minimumLength;
    }

    public void validate(String password, String username, String email) {
        if (password == null || password.length() < minimumLength || password.length() > 128) {
            throw new IllegalArgumentException("Password does not meet the configured length policy");
        }
        boolean upper = false, lower = false, digit = false, symbol = false;
        for (int i = 0; i < password.length(); i++) {
            char value = password.charAt(i);
            upper |= Character.isUpperCase(value);
            lower |= Character.isLowerCase(value);
            digit |= Character.isDigit(value);
            symbol |= !Character.isLetterOrDigit(value) && !Character.isWhitespace(value);
        }
        if (!(upper && lower && digit && symbol)) {
            throw new IllegalArgumentException("Password must contain upper, lower, digit and symbol characters");
        }
        String normalized = password.toLowerCase(Locale.ROOT);
        String normalizedUser = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String emailLocal = email == null ? "" : email.trim().toLowerCase(Locale.ROOT).split("@", 2)[0];
        if ((!normalizedUser.isBlank() && normalized.contains(normalizedUser))
                || (emailLocal.length() >= 4 && normalized.contains(emailLocal))) {
            throw new IllegalArgumentException("Password must not contain the username or email local-part");
        }
        if (normalized.contains("password") || normalized.contains("change_me") || normalized.contains("qwerty")) {
            throw new IllegalArgumentException("Password contains a prohibited weak pattern");
        }
    }
}
