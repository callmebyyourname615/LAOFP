package com.example.switching.security.filter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-client rate limiting on mutating endpoints (POST/PATCH).
 * Client identity = X-API-Key header value, falling back to remote IP.
 * Each client gets a token bucket refilling at {@code requestsPerMinute} tokens/min.
 * Disabled in the test profile via {@code switching.security.rate-limit.enabled=false}.
 */
@Component
@Order(10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final boolean enabled;
    private final int requestsPerMinute;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${switching.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${switching.security.rate-limit.requests-per-minute:100}") int requestsPerMinute) {
        this.enabled = enabled;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!enabled || !isMutatingRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = resolveClientId(request);
        Bucket bucket = buckets.computeIfAbsent(clientId, k -> buildBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded: clientId={} path={} method={}",
                    clientId, request.getRequestURI(), request.getMethod());
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"status":429,"error":"TOO_MANY_REQUESTS","errorCode":"REQ-004",\
                    "message":"Rate limit exceeded. Max %d requests per minute per client.",\
                    "path":"%s"}""".formatted(requestsPerMinute, request.getRequestURI()));
        }
    }

    /** Only rate-limit state-changing requests */
    private boolean isMutatingRequest(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    /** Use API key as client identity; fall back to IP address */
    private String resolveClientId(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return "key:" + apiKey;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private Bucket buildBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
