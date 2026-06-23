package com.example.switching.security.filter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.traffic.ratelimit.ParticipantIdentityResolver;
import com.example.switching.traffic.ratelimit.ParticipantTokenBucketService;
import com.example.switching.traffic.ratelimit.RateLimitDecision;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-participant token-bucket protection for state-changing requests.
 *
 * <p>The filter runs after Spring Security's filter chain, so authenticated API-key
 * and OAuth calls are keyed by participant bank code.  Legacy/unauthenticated calls
 * use a one-way API-key digest or remote address; plaintext credentials are never
 * retained, logged, or returned.</p>
 */
@Component
@Order(10)
public class RateLimitFilter extends OncePerRequestFilter {

    static final String HEADER_LIMIT = "X-RateLimit-Limit";
    static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    static final String HEADER_POLICY = "X-RateLimit-Policy";

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final long AUDIT_SUPPRESSION_SECONDS = 60;
    private static final int MAX_AUDIT_IDENTITIES = 100_000;

    private final boolean enabled;
    private final ParticipantTokenBucketService buckets;
    private final ParticipantIdentityResolver identityResolver;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<AuditLogService> auditLogProvider;
    private final ConcurrentHashMap<String, Long> lastAuditEpochSecond = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${switching.security.rate-limit.enabled:true}") boolean enabled,
            ParticipantTokenBucketService buckets,
            ParticipantIdentityResolver identityResolver,
            ObjectMapper objectMapper,
            ObjectProvider<AuditLogService> auditLogProvider) {
        this.enabled = enabled;
        this.buckets = buckets;
        this.identityResolver = identityResolver;
        this.objectMapper = objectMapper;
        this.auditLogProvider = auditLogProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!enabled || !isMutatingRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String identity = identityResolver.resolve(request);
        RateLimitDecision decision = buckets.consume(identity);
        response.setHeader(HEADER_LIMIT, Long.toString(decision.limit()));
        response.setHeader(HEADER_REMAINING, Long.toString(decision.remaining()));
        response.setHeader(HEADER_POLICY, shortRevision(decision.policyRevision()));

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfter = Math.max(1, decision.retryAfterSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        log.warn("Participant rate limit exceeded identity={} path={} method={} retryAfterSeconds={}",
                decision.identity(), request.getRequestURI(), request.getMethod(), retryAfter);
        auditRejection(decision, request, retryAfter);

        objectMapper.writeValue(response.getWriter(), Map.of(
                "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                "error", "TOO_MANY_REQUESTS",
                "errorCode", "REQ-004",
                "message", "Participant request quota exceeded",
                "retryAfterSeconds", retryAfter,
                "limit", decision.limit(),
                "policyRevision", shortRevision(decision.policyRevision()),
                "path", request.getRequestURI()));
    }

    private void auditRejection(
            RateLimitDecision decision,
            HttpServletRequest request,
            long retryAfter) {
        if (!shouldAudit(decision.identity())) {
            return;
        }
        AuditLogService auditLogService = auditLogProvider.getIfAvailable();
        if (auditLogService == null) {
            return;
        }
        try {
            auditLogService.log(
                    "PARTICIPANT_RATE_LIMIT_EXCEEDED",
                    "PARTICIPANT_TRAFFIC",
                    decision.identity(),
                    "RATE_LIMIT_FILTER",
                    Map.of(
                            "method", request.getMethod(),
                            "path", request.getRequestURI(),
                            "limit", decision.limit(),
                            "remaining", decision.remaining(),
                            "retryAfterSeconds", retryAfter,
                            "policyRevision", shortRevision(decision.policyRevision())));
        } catch (Exception exception) {
            log.error("Unable to persist rate-limit audit evidence identity={}",
                    decision.identity(), exception);
        }
    }

    private boolean shouldAudit(String identity) {
        long now = Instant.now().getEpochSecond();
        if (!lastAuditEpochSecond.containsKey(identity)
                && lastAuditEpochSecond.size() >= MAX_AUDIT_IDENTITIES) {
            return false;
        }
        Long previous = lastAuditEpochSecond.putIfAbsent(identity, now);
        if (previous == null) {
            return true;
        }
        return now - previous >= AUDIT_SUPPRESSION_SECONDS
                && lastAuditEpochSecond.replace(identity, previous, now);
    }

    private static boolean isMutatingRequest(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    private static String shortRevision(String revision) {
        if (revision == null || revision.isBlank()) {
            return "unknown";
        }
        return revision.substring(0, Math.min(16, revision.length()));
    }
}
