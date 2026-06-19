package com.example.switching.security.mtls;

/**
 * Thrown when a client certificate presented in the {@code X-Client-Cert}
 * header is missing, unparseable, not registered in {@code psp_certificates},
 * has been revoked, or has expired.
 *
 * <p>Mapped to HTTP 401 with error code {@code LFP-2002} by
 * {@code GlobalExceptionHandler}.
 */
public class MtlsCertInvalidException extends RuntimeException {

    public MtlsCertInvalidException(String message) {
        super(message);
    }
}
