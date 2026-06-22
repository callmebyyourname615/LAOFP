package com.example.switching.rtp.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.example.switching.rtp.entity.RtpRequestEntity;
import com.example.switching.webhook.service.WebhookEventPublisher;
import com.example.switching.common.PhaseIIAuditPublisher;

@Component
public class RtpDomainEventPublisher {
    private final WebhookEventPublisher webhook;
    private final PhaseIIAuditPublisher audit;
    public RtpDomainEventPublisher(WebhookEventPublisher webhook, PhaseIIAuditPublisher audit) { this.webhook = webhook; this.audit = audit; }

    public void publish(String event, RtpRequestEntity request, Map<String,Object> extra) {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("requestId", request.getId().toString());
        data.put("correlationId", request.getRequestCorrelationId());
        data.put("status", request.getStatus().name());
        data.put("requestedAmount", request.getRequestedAmount().toPlainString());
        data.put("authorisedAmount", request.getAuthorisedAmount().toPlainString());
        data.put("settledAmount", request.getSettledAmount().toPlainString());
        data.put("currency", request.getCurrency());
        if (extra != null) data.putAll(extra);
        audit.publish(event, "RTP", request.getId().toString(), "SYSTEM", data);
        webhook.publishQuietly(event, request.getPayeeParticipantId(), request.getId().toString(), data);
        if (!request.getPayerParticipantId().equals(request.getPayeeParticipantId())) {
            webhook.publishQuietly(event, request.getPayerParticipantId(), request.getId().toString(), data);
        }
    }
}
