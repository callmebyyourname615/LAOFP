package com.example.switching.webhook.crypto;

interface VaultTokenProvider {
    String token();

    default void invalidate() {
    }
}
