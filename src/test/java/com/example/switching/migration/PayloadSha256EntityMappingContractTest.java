package com.example.switching.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import com.example.switching.configchange.entity.ConfigurationChangeRequestEntity;
import com.example.switching.outbox.deadletter.entity.OutboxDeadLetterEntity;

/** Keeps the entity-side contract explicit so future edits cannot silently reintroduce CHAR/VARCHAR drift. */
class PayloadSha256EntityMappingContractTest {

    @Test
    void configurationChangeDigestMapsToRequiredVarchar64Contract() throws Exception {
        assertPayloadSha256Mapping(ConfigurationChangeRequestEntity.class);
    }

    @Test
    void deadLetterDigestMapsToRequiredVarchar64Contract() throws Exception {
        assertPayloadSha256Mapping(OutboxDeadLetterEntity.class);
    }

    private static void assertPayloadSha256Mapping(Class<?> entityType) throws Exception {
        Field field = entityType.getDeclaredField("payloadSha256");
        Column column = field.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("payload_sha256");
        assertThat(column.nullable()).isFalse();
        assertThat(column.length()).isEqualTo(64);
        assertThat(column.columnDefinition())
                .as("the database type must be controlled by Flyway V83, not hard-coded as CHAR")
                .isEmpty();
    }
}
