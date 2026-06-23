package com.example.switching.webhook.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

class WebhookEncryptionConfigurationContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(WebhookEncryptionConfiguration.class)
            .withPropertyValues(
                    "switching.webhook.encryption.provider=local",
                    "switching.webhook.encryption.local.master-key-base64="
                            + "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

    @Test
    void createsFallbackMapperWhenJacksonAutoConfigurationIsAbsent() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ObjectMapper.class);
            assertThat(context).hasSingleBean(KeyEncryptionService.class);
            assertThat(context).hasSingleBean(SecretEncryptionService.class);
        });
    }
    @Test
    void createsVaultTransitServiceWithoutGlobalObjectMapper() {
        new ApplicationContextRunner()
                .withUserConfiguration(WebhookEncryptionConfiguration.class)
                .withPropertyValues(
                        "switching.webhook.encryption.provider=vault-transit",
                        "switching.webhook.encryption.vault.address=https://vault.example.test",
                        "switching.webhook.encryption.vault.auth-method=token",
                        "switching.webhook.encryption.vault.token=test-token",
                        "switching.webhook.encryption.vault.mount=transit",
                        "switching.webhook.encryption.vault.key=switching-webhook")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ObjectMapper.class);
                    assertThat(context).hasSingleBean(KeyEncryptionService.class);
                    assertThat(context.getBean(KeyEncryptionService.class))
                            .isInstanceOf(VaultTransitKeyEncryptionService.class);
                    assertThat(context).hasSingleBean(SecretEncryptionService.class);
                });
    }

}
