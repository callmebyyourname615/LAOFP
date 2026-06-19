package com.example.switching.migration;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.switching.webhook.crypto.EncryptedSecret;
import com.example.switching.webhook.crypto.SecretEncryptionService;

/** Encrypts legacy webhook secret_plain values between Flyway V43 and V44. */
@Component
public class WebhookSecretBackfillService {

    private static final Logger log = LoggerFactory.getLogger(WebhookSecretBackfillService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final SecretEncryptionService secretEncryptionService;

    public WebhookSecretBackfillService(JdbcTemplate jdbcTemplate,
                                        TransactionTemplate transactionTemplate,
                                        SecretEncryptionService secretEncryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.secretEncryptionService = secretEncryptionService;
    }

    public int backfill() {
        List<LegacySecretRow> rows = jdbcTemplate.query(
                """
                SELECT id, secret_plain
                FROM webhook_registrations
                WHERE secret_ciphertext IS NULL
                ORDER BY id
                """,
                (resultSet, rowNumber) -> new LegacySecretRow(
                        resultSet.getLong("id"),
                        resultSet.getString("secret_plain")));

        int updated = 0;
        for (LegacySecretRow row : rows) {
            if (row.plaintext() == null || row.plaintext().isBlank()) {
                throw new IllegalStateException(
                        "Webhook registration " + row.id() + " has a blank legacy secret");
            }

            EncryptedSecret encrypted = secretEncryptionService.encrypt(row.plaintext());
            Integer rowCount = transactionTemplate.execute(status -> jdbcTemplate.update(
                    """
                    UPDATE webhook_registrations
                    SET secret_ciphertext = ?,
                        secret_key_id = ?,
                        secret_version = ?
                    WHERE id = ?
                      AND secret_ciphertext IS NULL
                    """,
                    encrypted.ciphertext(),
                    encrypted.keyId(),
                    encrypted.version(),
                    row.id()));
            updated += rowCount == null ? 0 : rowCount;
        }

        Integer remaining = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM webhook_registrations
                WHERE secret_ciphertext IS NULL OR secret_key_id IS NULL
                """,
                Integer.class);
        if (remaining == null || remaining != 0) {
            throw new IllegalStateException(
                    "Webhook secret encryption backfill incomplete; remaining rows=" + remaining);
        }

        log.info("Webhook secret encryption backfill completed: encryptedRows={}", updated);
        return updated;
    }

    private record LegacySecretRow(long id, String plaintext) {
    }
}
