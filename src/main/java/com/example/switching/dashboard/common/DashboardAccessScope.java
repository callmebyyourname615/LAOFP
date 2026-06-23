package com.example.switching.dashboard.common;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Defence-in-depth guard preventing participant-scoped sessions from viewing scheme-wide KPIs. */
@Component
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class DashboardAccessScope {
    public void requireSchemeWideOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("Dashboard authentication is required");
        }
        Object details = authentication.getDetails();
        if (details instanceof Map<?, ?> values && values.get("participantId") != null) {
            throw new AccessDeniedException("Participant-scoped sessions cannot access scheme-wide dashboards");
        }
    }
}
