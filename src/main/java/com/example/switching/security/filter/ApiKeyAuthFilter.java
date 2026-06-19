package com.example.switching.security.filter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.switching.security.entity.ApiKeyEntity;
import com.example.switching.security.repository.ApiKeyRepository;
import com.example.switching.security.util.ApiKeyHashUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyAuthFilter(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null && !apiKey.isBlank()) {
            String keyHash = ApiKeyHashUtil.hash(apiKey.trim());
            apiKeyRepository.findByKeyValueAndEnabledTrue(keyHash)
                    .ifPresent(key -> {
                        if (key.getExpiresAt() == null || !LocalDateTime.now().isAfter(key.getExpiresAt())) {
                            authenticate(key);
                            updateLastUsed(key);
                        }
                    });
        }

        chain.doFilter(request, response);
    }

    private void authenticate(ApiKeyEntity key) {
        var authority = new SimpleGrantedAuthority("ROLE_" + key.getRole().name());
        var auth = new UsernamePasswordAuthenticationToken(
                key.getName(),
                null,
                List.of(authority)
        );
        auth.setDetails(Map.of(
                "keyName", key.getName(),
                "role", key.getRole().name(),
                "bankCode", key.getBankCode() == null ? "" : key.getBankCode()));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void updateLastUsed(ApiKeyEntity key) {
        try {
            key.setLastUsedAt(LocalDateTime.now());
            apiKeyRepository.save(key);
        } catch (Exception ignored) {
            // Non-critical: do not fail the request if last_used_at update fails
        }
    }
}
