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
    void providesObjectMapperWhenFocusedContextHasNoJacksonAutoConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObjectMapper.class);
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
