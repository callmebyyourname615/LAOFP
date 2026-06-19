package com.example.switching.security.mtls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;

class TrustedProxyHeaderFilterTest {
    @Test
    void stripsSpoofedCertificateWithoutVerifiedMarker() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Client-Cert", "spoofed");
        request.addHeader(TrustedProxyHeaderFilter.VERIFIED_HEADER, "spoofed-success");
        AtomicReference<String> certificate = new AtomicReference<>();
        AtomicReference<Boolean> headerVisible = new AtomicReference<>(true);
        FilterChain chain = (req, res) -> {
            HttpServletRequest http = (HttpServletRequest) req;
            certificate.set(http.getHeader("X-Client-Cert"));
            headerVisible.set(Collections.list(http.getHeaderNames()).stream()
                    .anyMatch("X-Client-Cert"::equalsIgnoreCase));
        };

        new TrustedProxyHeaderFilter("X-Client-Cert")
                .doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(certificate.get());
        assertFalse(headerVisible.get());
    }

    @Test
    void preservesCertificateOnlyWithTrustedMarker() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Client-Cert", "encoded-pem");
        request.addHeader("ssl-client-verify", "SUCCESS");
        AtomicReference<String> certificate = new AtomicReference<>();
        AtomicReference<String> verified = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            HttpServletRequest http = (HttpServletRequest) req;
            certificate.set(http.getHeader("X-Client-Cert"));
            verified.set(http.getHeader(TrustedProxyHeaderFilter.VERIFIED_HEADER));
        };

        new TrustedProxyHeaderFilter("X-Client-Cert")
                .doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals("encoded-pem", certificate.get());
        assertEquals("SUCCESS", verified.get());
    }
}
