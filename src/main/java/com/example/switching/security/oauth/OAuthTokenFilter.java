package com.example.switching.security.oauth;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.switching.participant.exception.ParticipantSuspendedException;
import com.example.switching.security.oauth.service.OAuthTokenService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * P9 — OAuth 2.0 Bearer token filter.
 *
 * <p>Activates only when an {@code Authorization: Bearer <token>} header is
 * present.  If the header is absent the filter passes the request on to the
 * next filter in the chain (e.g. {@code ApiKeyAuthFilter}) so that the legacy
 * X-API-Key credential continues to work during the grace period when both
 * auth schemes must be supported simultaneously.
 *
 * <h3>Happy path</h3>
 * <ol>
 *   <li>Extract Bearer token from {@code Authorization} header.</li>
 *   <li>Delegate to {@link OAuthTokenService#validateToken(String)} — verifies
 *       signature, expiry, and revocation status.</li>
 *   <li>Populate {@link SecurityContextHolder} with the PSP's bank code as
 *       principal and {@code ROLE_BANK} as the granted authority.</li>
 *   <li>Continue the filter chain.</li>
 * </ol>
 *
 * <h3>Error path</h3>
 * <p>If {@link OAuthTokenInvalidException} is thrown the filter writes an
 * RFC 7807-compatible JSON 401 response (error code {@code LFP-2001}) and
 * stops the chain — no further filters or controllers are invoked.
 */
public class OAuthTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION  = "Authorization";

    private final OAuthTokenService tokenService;

    public OAuthTokenFilter(OAuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(AUTHORIZATION);

        // Only process Bearer tokens — other schemes (X-API-Key) are handled
        // by the downstream ApiKeyAuthFilter.
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            OAuthTokenClaims claims = tokenService.validateToken(authHeader);

            // PSP OAuth clients always map to ROLE_BANK.
            // Use pspId as principal so downstream services can identify the caller.
            var auth = new UsernamePasswordAuthenticationToken(
                    claims.pspId(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_BANK")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("OAuth Bearer auth OK: psp={} scopes={}", claims.pspId(), claims.scopes());

        } catch (ParticipantSuspendedException ex) {
            log.debug("OAuth Bearer auth FORBIDDEN: suspended PSP on {}: {}", request.getRequestURI(), ex.getMessage());
            writeForbidden(response, request.getRequestURI(), ex.getMessage());
            return;  // stop chain
        } catch (OAuthTokenInvalidException ex) {
            log.debug("OAuth Bearer auth FAILED: {} — {}", request.getRequestURI(), ex.getMessage());
            writeUnauthorized(response, request.getRequestURI(), ex.getMessage());
            return;  // stop chain — do NOT call chain.doFilter
        }

        chain.doFilter(request, response);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void writeForbidden(HttpServletResponse response,
                                String path,
                                String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"status":403,"error":"FORBIDDEN","errorCode":"LFP-2004",\
                "message":"%s","path":"%s"}""".formatted(
                escape(detail), escape(path)));
    }

    private void writeUnauthorized(HttpServletResponse response,
                                   String path,
                                   String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"status":401,"error":"UNAUTHORIZED","errorCode":"LFP-2001",\
                "message":"%s","path":"%s"}""".formatted(
                escape(detail), escape(path)));
    }

    /** Minimal JSON-safe escaping for the two fields we embed verbatim. */
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
