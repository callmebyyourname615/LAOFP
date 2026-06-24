package com.example.switching.webhook.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

class WebhookEncryptionConfigurationTest {

    private static final String LOCAL_MASTER_KEY =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(WebhookEncryptionConfiguration.class)
            .withPropertyValues(
                    "switching.webhook.encryption.provider=local",
                    "switching.webhook.encryption.local.master-key-base64=" + LOCAL_MASTER_KEY);

    @Test
    void instantiatesEncryptionServicesWithoutGlobalObjectMapperBean() {
        // Phase 69 redesigned WebhookEncryptionConfiguration to use an internal
        // fallback ObjectMapper (via ObjectProvider) when no Jackson autoconfig bean
        // is available. The configuration therefore does NOT register ObjectMapper
        // as a context bean — it only consumes one if present and otherwise uses
        // a private fallback. This test pins the intended behaviour.
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ObjectMapper.class);
            assertThat(context).hasSingleBean(SecretEncryptionService.class);
            assertThat(context).hasSingleBean(KeyEncryptionService.class);
        });
    }

    @Test
    void preservesApplicationProvidedObjectMapper() {
        ObjectMapper applicationMapper = JsonMapper.builder().build();

        contextRunner
                .withBean("applicationObjectMapper", ObjectMapper.class, () -> applicationMapper)
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    assertThat(context.getBean(ObjectMapper.class)).isSameAs(applicationMapper);
                });
    }
}
