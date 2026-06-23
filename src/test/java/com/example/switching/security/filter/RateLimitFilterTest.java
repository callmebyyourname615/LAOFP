package com.example.switching.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.switching.audit.service.AuditLogService;
import com.example.switching.traffic.ratelimit.ParticipantIdentityResolver;
import com.example.switching.traffic.ratelimit.ParticipantTokenBucketService;
import com.example.switching.traffic.ratelimit.RateLimitDecision;
import com.fasterxml.jackson.databind.ObjectMapper;

class RateLimitFilterTest {

    @Test
    void allowedMutationContinuesAndReturnsQuotaHeaders() throws Exception {
        ParticipantTokenBucketService buckets = mock(ParticipantTokenBucketService.class);
        ParticipantIdentityResolver identities = mock(ParticipantIdentityResolver.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AuditLogService> audits = mock(ObjectProvider.class);
        when(identities.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn("participant:BANK_A");
        when(buckets.consume("participant:BANK_A"))
                .thenReturn(new RateLimitDecision(true, 10, 9, 0, "abcdef0123456789", "participant:BANK_A"));
        RateLimitFilter filter = new RateLimitFilter(
                true, buckets, identities, new ObjectMapper(), audits);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transfer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getHeader(RateLimitFilter.HEADER_LIMIT)).isEqualTo("10");
        assertThat(response.getHeader(RateLimitFilter.HEADER_REMAINING)).isEqualTo("9");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectedMutationReturns429RetryAfterAndStructuredBody() throws Exception {
        ParticipantTokenBucketService buckets = mock(ParticipantTokenBucketService.class);
        ParticipantIdentityResolver identities = mock(ParticipantIdentityResolver.class);
        AuditLogService auditLog = mock(AuditLogService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AuditLogService> audits = mock(ObjectProvider.class);
        when(audits.getIfAvailable()).thenReturn(auditLog);
        when(identities.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn("participant:BANK_A");
        when(buckets.consume("participant:BANK_A"))
                .thenReturn(new RateLimitDecision(false, 10, 0, 7, "abcdef0123456789", "participant:BANK_A"));
        RateLimitFilter filter = new RateLimitFilter(
                true, buckets, identities, new ObjectMapper(), audits);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transfer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("7");
        assertThat(response.getContentAsString())
                .contains("TOO_MANY_REQUESTS", "REQ-004", "retryAfterSeconds");
        verify(auditLog).log(
                org.mockito.ArgumentMatchers.eq("PARTICIPANT_RATE_LIMIT_EXCEEDED"),
                org.mockito.ArgumentMatchers.eq("PARTICIPANT_TRAFFIC"),
                org.mockito.ArgumentMatchers.eq("participant:BANK_A"),
                org.mockito.ArgumentMatchers.eq("RATE_LIMIT_FILTER"),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void getRequestDoesNotConsumeQuota() throws Exception {
        ParticipantTokenBucketService buckets = mock(ParticipantTokenBucketService.class);
        ParticipantIdentityResolver identities = mock(ParticipantIdentityResolver.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AuditLogService> audits = mock(ObjectProvider.class);
        RateLimitFilter filter = new RateLimitFilter(
                true, buckets, identities, new ObjectMapper(), audits);

        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/transfer"),
                new MockHttpServletResponse(),
                new MockFilterChain());

        verifyNoInteractions(buckets, identities);
    }
}
