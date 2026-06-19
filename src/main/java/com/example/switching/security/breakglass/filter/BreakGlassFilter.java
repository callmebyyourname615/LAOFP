package com.example.switching.security.breakglass.filter;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.switching.security.breakglass.service.PrivilegedAccessService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class BreakGlassFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Break-Glass-Token";
    private final PrivilegedAccessService service;
    private final List<Pattern> protectedPaths;

    public BreakGlassFilter(PrivilegedAccessService service,
                            @Value("${switching.api.v1-prefix:/v1}") String apiV1Prefix) {
        this.service = service;
        String prefix = normalizePrefix(apiV1Prefix);
        String quoted = Pattern.quote(prefix);
        this.protectedPaths = List.of(
                Pattern.compile("^" + quoted + "/operations/dead-letters/[^/]+/execute-replay$"),
                Pattern.compile("^" + quoted + "/operations/dead-letters/[^/]+/discard$"),
                Pattern.compile("^" + quoted + "/operations/legal-holds/[^/]+/approve-release$"),
                Pattern.compile("^" + quoted + "/operations/config-changes/[^/]+/execute$")
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return protectedPaths.stream().noneMatch(pattern -> pattern.matcher(request.getRequestURI()).matches());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication == null ? null : authentication.getName();
        try {
            service.validateAndRecordUse(request.getHeader(HEADER), actor,
                    request.getMethod() + " " + request.getRequestURI());
            chain.doFilter(request, response);
        } catch (RuntimeException ex) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":403,\"error\":\"FORBIDDEN\",\"errorCode\":\"SEC-BG-001\",\"message\":\"Valid break-glass authorization required\"}");
        }
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "/v1";
        }
        String normalized = prefix.startsWith("/") ? prefix : "/" + prefix;
        return normalized.length() > 1 && normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }
}
