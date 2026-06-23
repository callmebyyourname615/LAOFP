package com.example.switching.usermgmt.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.example.switching.usermgmt.entity.UserEntity;
import com.example.switching.usermgmt.enums.UserStatus;
import com.example.switching.usermgmt.service.AuthorizationService;
import com.example.switching.usermgmt.service.SmosTokenClaims;
import com.example.switching.usermgmt.service.SmosTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@ConditionalOnProperty(name = "switching.smos.enabled", havingValue = "true")
public class SmosJwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String PREFIX = "Bearer " + SmosTokenService.TOKEN_PREFIX;
    private final SmosTokenService tokens;
    private final AuthorizationService authorization;
    public SmosJwtAuthenticationFilter(SmosTokenService tokens, AuthorizationService authorization) {
        this.tokens = tokens;
        this.authorization = authorization;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX)) { chain.doFilter(request, response); return; }
        try {
            SmosTokenClaims claims = tokens.validate(header.substring("Bearer ".length()));
            UserEntity user = authorization.requireUser(claims.username());
            if (!user.getId().equals(claims.userId()) || user.getStatus() != UserStatus.ACTIVE) {
                throw new IllegalArgumentException("SMOS user is disabled or token subject no longer matches");
            }
            var currentRoles = authorization.roles(user);
            var currentPermissions = authorization.permissions(user);
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            currentRoles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            currentPermissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(
                    "PERM_" + permission.replace('.', '_').toUpperCase(Locale.ROOT))));
            addCompatibilityAuthorities(currentRoles, authorities);
            var authentication = new UsernamePasswordAuthenticationToken(claims.username(), null, authorities);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("userId", claims.userId()); details.put("roles", currentRoles); details.put("permissions", currentPermissions);
            authentication.setDetails(Map.copyOf(details));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"error\":\"UNAUTHORIZED\",\"errorCode\":\"SMOS-401\",\"message\":\"Invalid or expired SMOS access token\"}");
        }
    }

    private static void addCompatibilityAuthorities(java.util.Set<String> roles, List<SimpleGrantedAuthority> authorities) {
        if (roles.contains("SYSTEM_ADMIN")) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            authorities.add(new SimpleGrantedAuthority("ROLE_OPS"));
        }
        if (roles.contains("OPS_ADMIN") || roles.contains("SETTLEMENT_OFFICER") || roles.contains("DISPUTE_OFFICER")
                || roles.contains("RISK_OFFICER") || roles.contains("AUDITOR") || roles.contains("READ_ONLY")) {
            authorities.add(new SimpleGrantedAuthority("ROLE_OPS"));
        }
        if (roles.contains("PARTICIPANT_ADMIN")) authorities.add(new SimpleGrantedAuthority("ROLE_BANK"));
    }
}
