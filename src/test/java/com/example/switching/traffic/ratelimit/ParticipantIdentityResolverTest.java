package com.example.switching.traffic.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ParticipantIdentityResolverTest {

    private final ParticipantIdentityResolver resolver = new ParticipantIdentityResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesApiKeyParticipantFromAuthenticatedDetails() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "key-name", null, List.of(new SimpleGrantedAuthority("ROLE_BANK")));
        authentication.setDetails(Map.of("bankCode", "bank_a"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(resolver.resolve(new MockHttpServletRequest()))
                .isEqualTo("participant:BANK_A");
    }

    @Test
    void hashesLegacyApiKeyInsteadOfRetainingPlaintext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "top-secret-key");

        String identity = resolver.resolve(request);

        assertThat(identity).startsWith("api-key-hash:");
        assertThat(identity).doesNotContain("top-secret-key");
    }
}
