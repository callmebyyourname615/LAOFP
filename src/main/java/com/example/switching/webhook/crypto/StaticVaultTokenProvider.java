package com.example.switching.webhook.crypto;

final class StaticVaultTokenProvider implements VaultTokenProvider {

    private final String token;

    StaticVaultTokenProvider(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Vault token is required for token auth");
        }
        this.token = token;
    }

    @Override
    public String token() {
        return token;
    }
}
