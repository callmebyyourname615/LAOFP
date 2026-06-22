package com.example.switching.common;

import org.springframework.stereotype.Component;
import com.example.switching.audit.service.AuditLogService;

@Component
public class PhaseIIAuditPublisher {
    private final AuditLogService audit;
    public PhaseIIAuditPublisher(AuditLogService audit) { this.audit = audit; }
    public void publish(String eventType, String referenceType, String referenceId, String actor, Object payload) {
        audit.log(eventType, referenceType, referenceId, actor == null ? "SYSTEM" : actor, payload);
    }
}
