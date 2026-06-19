package com.example.switching.webhook.crypto;

import java.net.http.HttpClient;
import java.security.SecureRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebhookEncryptionProperties.class)
public class WebhookEncryptionConfiguration {

    @Bean
    SecureRandom webhookSecretSecureRandom() {
        return new SecureRandom();
    }

    @Bean
    @ConditionalOnProperty(
            name = "switching.webhook.encryption.provider",
            havingValue = "vault-transit")
    KeyEncryptionService vaultTransitKeyEncryptionService(
            WebhookEncryptionProperties properties,
            ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        String authMethod = properties.getVault().getAuthMethod();
        VaultTokenProvider tokenProvider;
        if ("kubernetes".equalsIgnoreCase(authMethod)) {
            tokenProvider = new KubernetesVaultTokenProvider(httpClient, objectMapper, properties);
        } else if ("token".equalsIgnoreCase(authMethod)) {
            tokenProvider = new StaticVaultTokenProvider(properties.getVault().getToken());
        } else {
            throw new IllegalStateException("Unsupported Vault auth method: " + authMethod);
        }
        return new VaultTransitKeyEncryptionService(httpClient, objectMapper, properties, tokenProvider);
    }

    @Bean
    @Profile("!prod")
    @ConditionalOnProperty(
            name = "switching.webhook.encryption.provider",
            havingValue = "local",
            matchIfMissing = true)
    KeyEncryptionService localAesKeyEncryptionService(
            WebhookEncryptionProperties properties,
            SecureRandom secureRandom) {
        return new LocalAesKeyEncryptionService(
                properties.getLocal().getMasterKeyBase64(),
                secureRandom);
    }

    @Bean
    @ConditionalOnMissingBean(SecretEncryptionService.class)
    SecretEncryptionService envelopeSecretEncryptionService(
            KeyEncryptionService keyEncryptionService,
            ObjectMapper objectMapper,
            SecureRandom secureRandom) {
        return new EnvelopeSecretEncryptionService(keyEncryptionService, objectMapper, secureRandom);
    }
}
