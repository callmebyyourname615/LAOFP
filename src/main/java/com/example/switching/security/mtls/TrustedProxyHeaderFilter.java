package com.example.switching.security.mtls;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Removes spoofable client-certificate headers unless the trusted ingress
 * supplied a successful certificate-verification marker.
 *
 * <p>The application ingress must overwrite, rather than append, these headers.
 * Direct pod access must also be blocked with NetworkPolicy.</p>
 */
public class TrustedProxyHeaderFilter extends OncePerRequestFilter {
    public static final String VERIFIED_HEADER = "X-Client-Cert-Verified";
    public static final String INGRESS_VERIFIED_HEADER = "ssl-client-verify";

    private final String certificateHeader;

    public TrustedProxyHeaderFilter(String certificateHeader) {
        this.certificateHeader = certificateHeader;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        boolean verified = "SUCCESS".equalsIgnoreCase(request.getHeader(INGRESS_VERIFIED_HEADER));

        HttpServletRequestWrapper sanitized = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if (certificateHeader.equalsIgnoreCase(name) && !verified) {
                    return null;
                }
                if (VERIFIED_HEADER.equalsIgnoreCase(name)) {
                    return verified ? "SUCCESS" : null;
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (certificateHeader.equalsIgnoreCase(name) && !verified) {
                    return Collections.emptyEnumeration();
                }
                if (VERIFIED_HEADER.equalsIgnoreCase(name)) {
                    return verified
                            ? Collections.enumeration(List.of("SUCCESS"))
                            : Collections.emptyEnumeration();
                }
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                Enumeration<String> original = super.getHeaderNames();
                if (original == null) {
                    return Collections.emptyEnumeration();
                }
                List<String> names = new ArrayList<>();
                while (original.hasMoreElements()) {
                    String name = original.nextElement();
                    if (certificateHeader.equalsIgnoreCase(name) && !verified) {
                        continue;
                    }
                    if (VERIFIED_HEADER.equalsIgnoreCase(name) && !verified) {
                        continue;
                    }
                    names.add(name);
                }
                if (verified && names.stream().noneMatch(VERIFIED_HEADER::equalsIgnoreCase)) {
                    names.add(VERIFIED_HEADER);
                }
                return Collections.enumeration(names);
            }
        };
        chain.doFilter(sanitized, response);
    }
}
