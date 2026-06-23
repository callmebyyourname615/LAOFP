package com.example.switching.traffic.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class ParticipantIdentityResolver {

    public String resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String bankCode = bankCodeFromDetails(authentication.getDetails());
            if (bankCode != null) {
                return "participant:" + bankCode;
            }
            if (hasBankRole(authentication) && authentication.getPrincipal() != null) {
                String principal = safeIdentity(String.valueOf(authentication.getPrincipal()));
                if (principal != null) {
                    return "participant:" + principal;
                }
            }
            String principal = safeIdentity(authentication.getName());
            if (principal != null && !"anonymousUser".equalsIgnoreCase(principal)) {
                return "actor:" + principal;
            }
        }

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return "api-key-hash:" + sha256(apiKey.trim()).substring(0, 16);
        }
        return "remote:" + safeRemoteAddress(request.getRemoteAddr());
    }

    private static String bankCodeFromDetails(Object details) {
        if (!(details instanceof Map<?, ?> map)) {
            return null;
        }
        Object value = map.get("bankCode");
        return safeIdentity(value == null ? null : String.valueOf(value));
    }

    private static boolean hasBankRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_BANK".equals(authority.getAuthority()));
    }

    private static String safeIdentity(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z0-9_-]{2,64}") ? normalized : null;
    }

    private static String safeRemoteAddress(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9:._-]", "_").substring(0, Math.min(64, value.length()));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
