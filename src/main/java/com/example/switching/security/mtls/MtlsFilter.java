package com.example.switching.security.mtls;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * P9 — mTLS client-certificate enforcement filter.
 *
 * <p>When {@code switching.security.mtls.enabled=true} this filter intercepts
 * every non-public request and verifies that the client presented a valid,
 * registered X.509 certificate via the configurable header (default:
 * {@code ssl-client-cert}, as injected by nginx with
 * {@code ingress-nginx ssl-client-cert}).
 *
 * <h3>Error path</h3>
 * <p>If the certificate header is absent, unparseable, not registered, revoked,
 * or expired the filter writes a JSON 401 response (error code
 * {@code LFP-2002}) and stops the chain.
 *
 * <h3>Public paths (skipped)</h3>
 * <ul>
 *   <li>{@code /v1/oauth/token} — PSPs obtain tokens here using
 *       {@code client_id + client_secret}; mTLS is not required at this stage</li>
 *   <li>{@code /v1/oauth/token/revoke}</li>
 *   <li>{@code /actuator/health}, {@code /actuator/info}</li>
 * </ul>
 */
public class MtlsFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MtlsFilter.class);

    private final MtlsCertificateValidator validator;
    private final String certHeader;
    private final String apiV1Prefix;

    public MtlsFilter(MtlsCertificateValidator validator, String certHeader, String apiV1Prefix) {
        this.validator  = validator;
        this.certHeader = certHeader;
        this.apiV1Prefix = apiV1Prefix;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith(apiV1Prefix + "/oauth/token")
                || path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String certValue = request.getHeader(certHeader);

        if (!StringUtils.hasText(certValue)) {
            log.debug("mTLS cert header '{}' missing on {}", certHeader, request.getRequestURI());
            writeUnauthorized(response, request.getRequestURI(),
                    "mTLS client certificate is required");
            return;
        }

        try {
            validator.validate(certValue);
            log.debug("mTLS cert valid on {}", request.getRequestURI());
        } catch (MtlsCertInvalidException ex) {
            log.debug("mTLS cert rejected on {}: {}", request.getRequestURI(), ex.getMessage());
            writeUnauthorized(response, request.getRequestURI(), ex.getMessage());
            return;
        }

        chain.doFilter(request, response);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void writeUnauthorized(HttpServletResponse response,
                                   String path,
                                   String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"status":401,"error":"UNAUTHORIZED","errorCode":"LFP-2002",\
                "message":"%s","path":"%s"}""".formatted(
                escape(detail), escape(path)));
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
