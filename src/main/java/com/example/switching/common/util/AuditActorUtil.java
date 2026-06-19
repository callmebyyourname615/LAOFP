package com.example.switching.common.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuditActorUtil {

    private AuditActorUtil() {}

    /**
     * Returns the authenticated API key name for audit logging.
     * Falls back to "SYSTEM" when no authentication is present (e.g. scheduled workers).
     */
    public static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
            return auth.getName();
        }
        return "SYSTEM";
    }
}
