package com.example.switching.security.oauth.dto;

/**
 * RFC 7009 §2.1 — token revocation request.
 * Received as {@code application/x-www-form-urlencoded}.
 */
public record OAuthRevokeRequest(String token) {}
