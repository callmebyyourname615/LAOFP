package com.example.switching.config;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs only in the "prod" profile. Validates runtime configuration that cannot be
 * enforced by YAML placeholder syntax alone. Fails startup immediately on any violation
 * so misconfigured deployments are caught before accepting traffic.
 */
@Component
@Profile("prod")
public class ProductionStartupValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ProductionStartupValidator.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.kafka.bootstrap-servers}")
    private String kafkaBootstrapServers;

    @Value("${spring.kafka.properties.security.protocol:}")
    private String kafkaSecurityProtocol;

    @Value("${spring.kafka.properties.sasl.mechanism:}")
    private String kafkaSaslMechanism;

    @Value("${spring.kafka.properties.sasl.jaas.config:}")
    private String kafkaSaslJaasConfig;

    @Value("${spring.kafka.properties.ssl.endpoint.identification.algorithm:}")
    private String kafkaEndpointIdentificationAlgorithm;

    @Value("${spring.kafka.properties.ssl.truststore.location:}")
    private String kafkaTruststoreLocation;

    @Value("${switching.account-lookup.base-url}")
    private String accountLookupBaseUrl;

    @Value("${switching.settlement.bol-rtgs-url}")
    private String bolRtgsUrl;

    @Value("${switching.archive.archive-db-url}")
    private String archiveDbUrl;

    @Value("${switching.archive.archive-db-password}")
    private String archiveDbPassword;

    @Value("${switching.archive.object-storage.endpoint}")
    private String objectStorageEndpoint;

    @Value("${switching.archive.object-storage.access-key}")
    private String objectStorageAccessKey;

    @Value("${switching.archive.object-storage.secret-key}")
    private String objectStorageSecretKey;

    @Value("${switching.aml.bol-fiu-url}")
    private String bolFiuUrl;

    @Value("${switching.aml.bol-fiu-api-key}")
    private String bolFiuApiKey;

    @Value("${switching.aml.sanctions.bol.enabled:true}")
    private boolean bolSanctionsEnabled;

    @Value("${switching.aml.sanctions.bol.url:}")
    private String bolSanctionsUrl;

    @Value("${switching.aml.sanctions.ofac.enabled:true}")
    private boolean ofacSanctionsEnabled;

    @Value("${switching.aml.sanctions.ofac.url:}")
    private String ofacSanctionsUrl;

    @Value("${switching.aml.sanctions.un.enabled:true}")
    private boolean unSanctionsEnabled;

    @Value("${switching.aml.sanctions.un.url:}")
    private String unSanctionsUrl;

    @Value("${switching.aml.sanctions.maximum-age:30h}")
    private java.time.Duration sanctionsMaximumAge;

    @Value("${switching.crossborder.promptpay-url}")
    private String promptpayUrl;

    @Value("${switching.crossborder.cnaps-url}")
    private String cnapsUrl;

    @Value("${switching.crossborder.napas-url}")
    private String napasUrl;

    @Value("${switching.crossborder.swift-url}")
    private String swiftUrl;

    @Value("${switching.security.message-crypto-key-base64}")
    private String messageCryptoKeyBase64;

    @Value("${switching.security.oauth.jwt-secret}")
    private String oauthJwtSecret;

    @Value("${switching.webhook.endpoint-policy.enabled:true}")
    private boolean webhookEndpointPolicyEnabled;

    @Value("${switching.webhook.endpoint-policy.require-https:true}")
    private boolean webhookRequireHttps;

    @Value("${switching.webhook.endpoint-policy.require-allowlist:true}")
    private boolean webhookRequireAllowlist;

    @Value("${switching.webhook.endpoint-policy.allowed-hosts:}")
    private String webhookAllowedHosts;

    @Value("${switching.webhook.endpoint-policy.proxy-enabled:false}")
    private boolean webhookProxyEnabled;

    @Value("${switching.webhook.endpoint-policy.proxy-host:}")
    private String webhookProxyHost;

    @Value("${switching.webhook.endpoint-policy.proxy-port:0}")
    private int webhookProxyPort;

    @Value("${switching.security.mtls.cert-header:}")
    private String mtlsCertHeader;

    @Value("${switching.webhook.encryption.provider:}")
    private String webhookEncryptionProvider;

    @Value("${switching.webhook.encryption.vault.address:}")
    private String webhookVaultAddress;

    @Value("${switching.webhook.encryption.vault.token:}")
    private String webhookVaultToken;

    @Value("${switching.webhook.encryption.vault.auth-method:token}")
    private String webhookVaultAuthMethod;

    @Value("${switching.webhook.encryption.vault.kubernetes-role:}")
    private String webhookVaultKubernetesRole;

    @Value("${switching.webhook.encryption.vault.service-account-token-file:}")
    private String webhookVaultServiceAccountTokenFile;

    @Value("${switching.webhook.encryption.vault.mount:}")
    private String webhookVaultMount;

    @Value("${switching.webhook.encryption.vault.key:}")
    private String webhookVaultKey;

    @Value("${switching.smos.enabled:false}")
    private boolean smosEnabled;

    @Value("${switching.smos.mfa-required:true}")
    private boolean smosMfaRequired;

    @Value("${switching.smos.jwt-secret:}")
    private String smosJwtSecret;

    @Value("${switching.smos.bootstrap.enabled:false}")
    private boolean smosBootstrapEnabled;

    @Value("${switching.security.oauth.enabled}")
    private boolean oauthEnabled;

    @Value("${switching.security.mtls.enabled}")
    private boolean mtlsEnabled;

    @Value("${switching.security.signing.enabled}")
    private boolean signingEnabled;

    @Value("${switching.observability.environment:}")
    private String observabilityEnvironment;

    @Value("${switching.observability.operational-metrics.enabled:true}")
    private boolean operationalMetricsEnabled;

    @Value("${switching.payment.json-initiation.enabled}")
    private boolean jsonInitiationEnabled;

    @Value("${switching.mock-bank.pacs002.force-reject}")
    private boolean mockForceReject;

    @Value("${switching.outbox.schema.allow-legacy-messages:false}")
    private boolean outboxAllowLegacyMessages;

    @Value("${switching.read-replica.enabled:false}")
    private boolean readReplicaEnabled;

    @Value("${switching.read-replica.url:}")
    private String readReplicaUrl;

    @Value("${switching.read-replica.username:}")
    private String readReplicaUsername;

    @Value("${switching.read-replica.password:}")
    private String readReplicaPassword;

    @Value("${management.tracing.enabled:false}")
    private boolean tracingEnabled;

    @Value("${management.otlp.tracing.endpoint:}")
    private String otlpTracingEndpoint;

    @Value("${switching.sql-inspection.enabled:false}")
    private boolean sqlInspectionEnabled;

    public ProductionStartupValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> violations = new ArrayList<>();

        requirePostgresVerifyFull(violations, "spring.datasource.url", dbUrl);
        requirePostgresVerifyFull(violations, "switching.archive.archive-db-url", archiveDbUrl);
        if (!readReplicaEnabled) {
            violations.add("switching.read-replica.enabled must be true in production.");
        } else {
            requirePostgresVerifyFull(violations, "switching.read-replica.url", readReplicaUrl);
            rejectBlankOrPlaceholder(violations, "switching.read-replica.username", readReplicaUsername);
            rejectBlankOrPlaceholder(violations, "switching.read-replica.password", readReplicaPassword);
            if (dbUrl.equals(readReplicaUrl)) {
                violations.add("Read replica URL must not be identical to the primary DB URL.");
            }
        }
        if (!tracingEnabled) {
            violations.add("management.tracing.enabled must be true in production.");
        }
        rejectBlankOrPlaceholder(violations, "management.otlp.tracing.endpoint", otlpTracingEndpoint);
        rejectHttpsUrl(violations, "management.otlp.tracing.endpoint", otlpTracingEndpoint);
        if (sqlInspectionEnabled) {
            violations.add("switching.sql-inspection.enabled must be false in production; enable it only during controlled UAT diagnostics.");
        }

        if (dbUrl.contains("localhost") || dbUrl.contains("127.0.0.1")) {
            violations.add("DB URL points to localhost — production must use a remote database host.");
        }

        if (mockForceReject) {
            violations.add("switching.mock-bank.pacs002.force-reject must not be true in production. "
                    + "This is a mock testing flag.");
        }
        if (outboxAllowLegacyMessages) {
            violations.add("switching.outbox.schema.allow-legacy-messages must be false in production.");
        }
        if (!"production".equalsIgnoreCase(observabilityEnvironment)) {
            violations.add("switching.observability.environment must be production in the prod profile.");
        }
        if (!operationalMetricsEnabled) {
            violations.add("switching.observability.operational-metrics.enabled must be true in production.");
        }
        if (jsonInitiationEnabled) {
            violations.add("switching.payment.json-initiation.enabled must be false in production; ISO initiation is required.");
        }

        rejectBlankOrPlaceholder(violations, "spring.kafka.bootstrap-servers", kafkaBootstrapServers);
        rejectBlankOrPlaceholder(violations, "spring.kafka.properties.security.protocol", kafkaSecurityProtocol);
        rejectBlankOrPlaceholder(violations, "spring.kafka.properties.sasl.mechanism", kafkaSaslMechanism);
        rejectBlankOrPlaceholder(violations, "spring.kafka.properties.sasl.jaas.config", kafkaSaslJaasConfig);
        rejectBlankOrPlaceholder(violations, "spring.kafka.properties.ssl.endpoint.identification.algorithm",
                kafkaEndpointIdentificationAlgorithm);
        rejectBlankOrPlaceholder(violations, "spring.kafka.properties.ssl.truststore.location", kafkaTruststoreLocation);
        rejectBlankOrPlaceholder(violations, "switching.account-lookup.base-url", accountLookupBaseUrl);
        rejectBlankOrPlaceholder(violations, "switching.settlement.bol-rtgs-url", bolRtgsUrl);
        rejectBlankOrPlaceholder(violations, "switching.archive.archive-db-url", archiveDbUrl);
        rejectBlankOrPlaceholder(violations, "switching.archive.archive-db-password", archiveDbPassword);
        rejectBlankOrPlaceholder(violations, "switching.archive.object-storage.endpoint", objectStorageEndpoint);
        rejectBlankOrPlaceholder(violations, "switching.archive.object-storage.access-key", objectStorageAccessKey);
        rejectBlankOrPlaceholder(violations, "switching.archive.object-storage.secret-key", objectStorageSecretKey);
        rejectBlankOrPlaceholder(violations, "switching.aml.bol-fiu-url", bolFiuUrl);
        rejectBlankOrPlaceholder(violations, "switching.aml.bol-fiu-api-key", bolFiuApiKey);
        if (bolSanctionsEnabled) {
            rejectBlankOrPlaceholder(violations, "switching.aml.sanctions.bol.url", bolSanctionsUrl);
            rejectHttpsUrl(violations, "switching.aml.sanctions.bol.url", bolSanctionsUrl);
        }
        if (ofacSanctionsEnabled) {
            rejectBlankOrPlaceholder(violations, "switching.aml.sanctions.ofac.url", ofacSanctionsUrl);
            rejectHttpsUrl(violations, "switching.aml.sanctions.ofac.url", ofacSanctionsUrl);
        }
        if (unSanctionsEnabled) {
            rejectBlankOrPlaceholder(violations, "switching.aml.sanctions.un.url", unSanctionsUrl);
            rejectHttpsUrl(violations, "switching.aml.sanctions.un.url", unSanctionsUrl);
        }
        rejectBlankOrPlaceholder(violations, "switching.crossborder.promptpay-url", promptpayUrl);
        rejectBlankOrPlaceholder(violations, "switching.crossborder.cnaps-url", cnapsUrl);
        rejectBlankOrPlaceholder(violations, "switching.crossborder.napas-url", napasUrl);
        rejectBlankOrPlaceholder(violations, "switching.crossborder.swift-url", swiftUrl);
        rejectBlankOrPlaceholder(violations, "switching.security.message-crypto-key-base64", messageCryptoKeyBase64);
        rejectBlankOrPlaceholder(violations, "switching.security.oauth.jwt-secret", oauthJwtSecret);
        rejectBlankOrPlaceholder(violations, "switching.smos.jwt-secret", smosJwtSecret);
        if (smosJwtSecret != null && smosJwtSecret.length() < 32) {
            violations.add("switching.smos.jwt-secret must contain at least 32 characters in production.");
        }
        if (smosJwtSecret != null && smosJwtSecret.equals(oauthJwtSecret)) {
            violations.add("switching.smos.jwt-secret must be different from the PSP OAuth signing secret.");
        }
        rejectBlankOrPlaceholder(violations, "switching.webhook.encryption.provider", webhookEncryptionProvider);
        rejectBlankOrPlaceholder(violations, "switching.webhook.encryption.vault.address", webhookVaultAddress);
        rejectBlankOrPlaceholder(violations, "switching.webhook.encryption.vault.auth-method", webhookVaultAuthMethod);
        if ("token".equalsIgnoreCase(webhookVaultAuthMethod)) {
            rejectBlankOrPlaceholder(violations, "switching.webhook.encryption.vault.token", webhookVaultToken);
        } else if ("kubernetes".equalsIgnoreCase(webhookVaultAuthMethod)) {
            rejectBlankOrPlaceholder(violations, "switching.webhook.encryption.vault.kubernetes-role", webhookVaultKubernetesRole);
            rejectBlankOrPlaceholder(violations, "switching.webhook.encryption.vault.service-account-token-file", webhookVaultServiceAccountTokenFile);
        } else {
            violations.add("switching.webhook.encryption.vault.auth-method must be kubernetes in production.");
        }
        rejectBlankOrPlaceholder(violations, "switching.webhook.encryption.vault.mount", webhookVaultMount);
        rejectBlankOrPlaceholder(violations, "switching.webhook.encryption.vault.key", webhookVaultKey);

        rejectLocalOrMockUrl(violations, "spring.kafka.bootstrap-servers", kafkaBootstrapServers);
        rejectLocalOrMockUrl(violations, "switching.account-lookup.base-url", accountLookupBaseUrl);
        rejectLocalOrMockUrl(violations, "switching.settlement.bol-rtgs-url", bolRtgsUrl);
        rejectLocalOrMockUrl(violations, "switching.archive.archive-db-url", archiveDbUrl);
        rejectLocalOrMockUrl(violations, "switching.archive.object-storage.endpoint", objectStorageEndpoint);
        rejectLocalOrMockUrl(violations, "switching.aml.bol-fiu-url", bolFiuUrl);
        rejectLocalOrMockUrl(violations, "switching.crossborder.promptpay-url", promptpayUrl);
        rejectLocalOrMockUrl(violations, "switching.crossborder.cnaps-url", cnapsUrl);
        rejectLocalOrMockUrl(violations, "switching.crossborder.napas-url", napasUrl);
        rejectLocalOrMockUrl(violations, "switching.crossborder.swift-url", swiftUrl);
        rejectLocalOrMockUrl(violations, "switching.webhook.encryption.vault.address", webhookVaultAddress);

        if (!webhookEndpointPolicyEnabled) {
            violations.add("switching.webhook.endpoint-policy.enabled must be true in production.");
        }
        if (!webhookRequireHttps) {
            violations.add("switching.webhook.endpoint-policy.require-https must be true in production.");
        }
        if (!webhookRequireAllowlist) {
            violations.add("switching.webhook.endpoint-policy.require-allowlist must be true in production.");
        }
        rejectBlankOrPlaceholder(violations, "switching.webhook.endpoint-policy.allowed-hosts", webhookAllowedHosts);
        if (!webhookProxyEnabled) {
            violations.add("switching.webhook.endpoint-policy.proxy-enabled must be true in production to close DNS rebinding TOCTOU risk.");
        }
        rejectBlankOrPlaceholder(violations, "switching.webhook.endpoint-policy.proxy-host", webhookProxyHost);
        if (webhookProxyPort < 1 || webhookProxyPort > 65535) {
            violations.add("switching.webhook.endpoint-policy.proxy-port must be between 1 and 65535.");
        }
        rejectLocalOrMockUrl(violations, "switching.webhook.endpoint-policy.proxy-host", webhookProxyHost);
        if (webhookAllowedHosts != null && (webhookAllowedHosts.contains("localhost")
                || webhookAllowedHosts.contains("127.0.0.1")
                || webhookAllowedHosts.contains("0.0.0.0"))) {
            violations.add("switching.webhook.endpoint-policy.allowed-hosts contains a local address.");
        }
        if (!"ssl-client-cert".equalsIgnoreCase(mtlsCertHeader)) {
            violations.add("switching.security.mtls.cert-header must use ingress-nginx trusted ssl-client-cert in production.");
        }

        if (!"vault-transit".equalsIgnoreCase(webhookEncryptionProvider)) {
            violations.add("switching.webhook.encryption.provider must be vault-transit in production; local/static keys are forbidden.");
        }
        if (!"kubernetes".equalsIgnoreCase(webhookVaultAuthMethod)) {
            violations.add("switching.webhook.encryption.vault.auth-method must be kubernetes in production; static Vault tokens are forbidden.");
        }
        if (webhookVaultToken != null && !webhookVaultToken.isBlank()) {
            violations.add("switching.webhook.encryption.vault.token must be blank in production.");
        }
        if (webhookVaultAddress != null && !webhookVaultAddress.startsWith("https://")) {
            violations.add("switching.webhook.encryption.vault.address must use HTTPS in production.");
        }

        if (!smosEnabled) {
            violations.add("switching.smos.enabled must be true in production.");
        }
        if (!smosMfaRequired) {
            violations.add("switching.smos.mfa-required must be true in production.");
        }
        if (smosBootstrapEnabled) {
            violations.add("switching.smos.bootstrap.enabled must be false in production after operator provisioning.");
        }
        if (!oauthEnabled) {
            violations.add("switching.security.oauth.enabled must be true in production.");
        }
        if (!mtlsEnabled) {
            violations.add("switching.security.mtls.enabled must be true in production.");
        }
        if (!signingEnabled) {
            violations.add("switching.security.signing.enabled must be true in production.");
        }
        if (!("SASL_SSL".equalsIgnoreCase(kafkaSecurityProtocol)
                || "SSL".equalsIgnoreCase(kafkaSecurityProtocol))) {
            violations.add("spring.kafka.properties.security.protocol must be SASL_SSL or SSL in production.");
        }
        if (!"https".equalsIgnoreCase(kafkaEndpointIdentificationAlgorithm)) {
            violations.add("Kafka hostname verification must remain enabled with ssl.endpoint.identification.algorithm=https.");
        }
        if (kafkaSecurityProtocol != null
                && kafkaSecurityProtocol.toUpperCase().contains("SASL")
                && (kafkaSaslJaasConfig == null || !kafkaSaslJaasConfig.contains("username="))) {
            violations.add("spring.kafka.properties.sasl.jaas.config must contain Kafka SASL credentials in production.");
        }

        validateSeedDataRemoved(violations);
        validateSanctionsDataLoaded(violations);
        validateWebhookSecretEncryption(violations);

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Production startup validation failed with " + violations.size() + " violation(s):\n"
                    + String.join("\n  - ", violations));
        }

        log.info("[PROD] Startup validation passed.");
    }

    private void requirePostgresVerifyFull(List<String> violations, String property, String value) {
        if (value == null || value.isBlank()) {
            violations.add(property + " must be set explicitly in production.");
            return;
        }
        String normalized = value.toLowerCase();
        if (!normalized.contains("sslmode=verify-full")) {
            violations.add(property + " must use sslmode=verify-full in production.");
        }
        if (!normalized.contains("sslrootcert=")) {
            violations.add(property + " must set sslrootcert in production.");
        }
    }

    private void rejectBlankOrPlaceholder(List<String> violations, String property, String value) {
        if (value == null || value.isBlank()) {
            violations.add(property + " must be set explicitly in production.");
            return;
        }

        String normalized = value.trim().toLowerCase();
        if (normalized.contains("replace_me")
                || normalized.contains("replace_with")
                || normalized.contains("change_me")
                || normalized.contains("dev-")
                || normalized.contains("test-secret")) {
            violations.add(property + " contains a placeholder/dev value and must be rotated for production.");
        }
    }

    private void rejectLocalOrMockUrl(List<String> violations, String property, String value) {
        if (value == null) {
            return;
        }

        String normalized = value.toLowerCase();
        if (normalized.contains("localhost")
                || normalized.contains("127.0.0.1")
                || normalized.contains("mock-")) {
            violations.add(property + " points to localhost/mock infrastructure and must be replaced for production.");
        }
    }

    private void rejectHttpsUrl(List<String> violations, String property, String value) {
        if (value != null && !value.isBlank() && !value.startsWith("https://")) {
            violations.add(property + " must use HTTPS in production.");
        }
    }

    private void validateSeedDataRemoved(List<String> violations) {
        rejectIfCountPositive(violations,
                "Seed API keys from V13 are still active. Rotate/delete sk-admin/sk-ops/sk-bank-* seed keys before production.",
                """
                SELECT COUNT(*)
                FROM api_keys
                WHERE enabled = TRUE
                  AND key_prefix IN ('sk-admin-switc', 'sk-ops-switchi', 'sk-bank-a-swit', 'sk-bank-b-swit')
                """);

        rejectIfCountPositive(violations,
                "Seed OAuth clients from V13 are still active. Rotate/delete client-bank-a/client-bank-b before production.",
                """
                SELECT COUNT(*)
                FROM oauth_clients
                WHERE status = 'ACTIVE'
                  AND client_id IN ('client-bank-a', 'client-bank-b')
                """);

        rejectIfCountPositive(violations,
                "Seed mTLS certificates from V13 are still active. Register real PSP certificates before production.",
                """
                SELECT COUNT(*)
                FROM psp_certificates
                WHERE status = 'ACTIVE'
                  AND cert_id IN ('seed-bank-a-mtls-cert', 'seed-bank-b-mtls-cert')
                """);

        rejectIfCountPositive(violations,
                "Mock connector routes are still enabled. Replace V13 mock connector configs/routing rules before production.",
                """
                SELECT COUNT(*)
                FROM connector_configs c
                LEFT JOIN routing_rules r ON r.connector_name = c.connector_name
                WHERE c.connector_type = 'MOCK'
                  AND (c.enabled = TRUE OR r.enabled = TRUE)
                """);

        rejectIfCountPositive(violations,
                "Mock billers from V37 are still active. Replace demo biller endpoints before production.",
                """
                SELECT COUNT(*)
                FROM billers
                WHERE status = 'ACTIVE'
                  AND api_url LIKE 'http://mock-biller:%'
                """);
    }

    private void validateSanctionsDataLoaded(List<String> violations) {
        validateProviderSanctionsData(violations, "BOL", bolSanctionsEnabled);
        validateProviderSanctionsData(violations, "OFAC", ofacSanctionsEnabled);
        validateProviderSanctionsData(violations, "UN", unSanctionsEnabled);
    }

    private void validateProviderSanctionsData(List<String> violations,
                                                String provider,
                                                boolean enabled) {
        if (!enabled) {
            return;
        }
        Integer active = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sanctions_lists
                 WHERE list_type = ? AND is_active = TRUE
                """, Integer.class, provider);
        if (active == null || active == 0) {
            violations.add("sanctions_lists has no active " + provider
                    + " rows. Complete a successful provider import before production.");
        }

        java.sql.Timestamp completed = jdbcTemplate.queryForObject("""
                SELECT MAX(completed_at)
                  FROM sanctions_import_runs
                 WHERE provider_code = ? AND status = 'SUCCESS'
                """, java.sql.Timestamp.class, provider);
        if (completed == null) {
            violations.add("No successful sanctions import audit exists for " + provider + ".");
            return;
        }
        java.time.Duration age = java.time.Duration.between(
                completed.toInstant(), java.time.Instant.now());
        if (age.compareTo(sanctionsMaximumAge) > 0) {
            violations.add(provider + " sanctions data is stale: age=" + age
                    + ", maximum=" + sanctionsMaximumAge + ".");
        }
    }

    private void validateWebhookSecretEncryption(List<String> violations) {
        Integer plaintextColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'webhook_registrations'
                  AND column_name = 'secret_plain'
                """, Integer.class);
        if (plaintextColumnCount != null && plaintextColumnCount > 0) {
            violations.add("webhook_registrations.secret_plain still exists. Run the dedicated migration Job through V44 before production startup.");
        }

        Integer incompleteRows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM webhook_registrations
                WHERE secret_ciphertext IS NULL OR secret_key_id IS NULL
                """, Integer.class);
        if (incompleteRows != null && incompleteRows > 0) {
            violations.add("webhook_registrations contains rows without encrypted signing secrets.");
        }
    }

    private void rejectIfCountPositive(List<String> violations, String message, String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        if (count != null && count > 0) {
            violations.add(message);
        }
    }
}
