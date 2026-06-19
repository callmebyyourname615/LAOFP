package com.example.switching.maintenance.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.config.ArchiveProperties;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;

@Profile("!migration")
@Service
public class ArchiveWorkerService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveWorkerService.class);
    private static final DateTimeFormatter DAY_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final JdbcTemplate primaryJdbcTemplate;
    private final JdbcTemplate archiveJdbcTemplate;
    private final ArchiveProperties archiveProperties;
    private final SchedulerLockService schedulerLockService;
    private final PartitionMaintenanceService partitionMaintenanceService;
    private final MinioClient minioClient;

    public ArchiveWorkerService(
            JdbcTemplate primaryJdbcTemplate,
            @Qualifier("archiveJdbcTemplate") JdbcTemplate archiveJdbcTemplate,
            ArchiveProperties archiveProperties,
            SchedulerLockService schedulerLockService,
            PartitionMaintenanceService partitionMaintenanceService) {
        this.primaryJdbcTemplate = primaryJdbcTemplate;
        this.archiveJdbcTemplate = archiveJdbcTemplate;
        this.archiveProperties = archiveProperties;
        this.schedulerLockService = schedulerLockService;
        this.partitionMaintenanceService = partitionMaintenanceService;
        this.minioClient = MinioClient.builder()
                .endpoint(archiveProperties.getObjectStorage().getEndpoint())
                .credentials(
                        archiveProperties.getObjectStorage().getAccessKey(),
                        archiveProperties.getObjectStorage().getSecretKey())
                .build();
    }

    @Scheduled(cron = "${switching.archive.archive-cron:0 15 1 * * *}")
    public void runDailyArchive() {
        if (!archiveProperties.isWorkerEnabled()) {
            return;
        }
        if (!schedulerLockService.acquire("switching-archive-worker", 180)) {
            log.debug("Archive worker skipped because another instance owns the lock");
            return;
        }
        try {
            archiveBusinessDate(LocalDate.now().minusDays(archiveProperties.getHotRetentionDays() + 1L));
        } finally {
            schedulerLockService.release("switching-archive-worker");
        }
    }

    @Transactional
    public void archiveBusinessDate(LocalDate businessDate) {
        try {
            ensureBucket();
            ArchiveRun run = new ArchiveRun(businessDate);
            archiveIsoPayloads(run);
            archivePaymentFlows(run);
            archiveInquiries(run);
            archiveTransactions(run);
            archiveTransactionStatusHistory(run);
            archiveTransactionEvents(run);
            archiveIsoMessages(run);
            archiveIsoValidationErrors(run);
            archiveSettlementItems(run);
            archiveReconciliationItems(run);
            writeManifest(run);
            verifyAndDropPartitions(run);
            log.info("Archive completed for businessDate={} tables={} rows={}",
                    businessDate, run.tableCounts.size(), run.totalRows());
        } catch (Exception ex) {
            log.error("Archive failed for businessDate={}", businessDate, ex);
            throw new IllegalStateException("Archive failed for businessDate=" + businessDate, ex);
        }
    }

    private void archivePaymentFlows(ArchiveRun run) {
        archiveRows(run, "payment_flows", "original_business_date", """
                SELECT id, flow_ref, source_bank, destination_bank, amount, currency, status, business_date
                FROM payment_flows
                WHERE business_date = ?
                """, row -> archiveJdbcTemplate.update("""
                INSERT INTO payment_flows_archive (
                    original_id, flow_ref, source_bank, destination_bank, amount, currency,
                    status, original_business_date, object_id
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, NULL
                WHERE NOT EXISTS (
                    SELECT 1 FROM payment_flows_archive WHERE original_id = ?
                )
                """,
                row.get("id"), row.get("flow_ref"), row.get("source_bank"), row.get("destination_bank"),
                row.get("amount"), row.get("currency"), row.get("status"), row.get("business_date"),
                row.get("id")));
    }

    private void archiveInquiries(ArchiveRun run) {
        archiveRows(run, "inquiries", "original_business_date", """
                SELECT id, inquiry_ref, source_bank, destination_bank, creditor_account, status, business_date
                FROM inquiries
                WHERE business_date = ?
                """, row -> archiveJdbcTemplate.update("""
                INSERT INTO inquiries_archive (
                    original_id, inquiry_ref, source_bank, destination_bank, creditor_account,
                    status, original_business_date, object_id
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, NULL
                WHERE NOT EXISTS (
                    SELECT 1 FROM inquiries_archive WHERE original_id = ?
                )
                """,
                row.get("id"), row.get("inquiry_ref"), row.get("source_bank"), row.get("destination_bank"),
                row.get("creditor_account"), row.get("status"), row.get("business_date"), row.get("id")));
    }

    private void archiveTransactions(ArchiveRun run) {
        archiveRows(run, "transactions", "original_business_date", """
                SELECT id, transaction_ref, source_bank, destination_bank, source_account_no,
                       destination_account_no, amount, currency, status, business_date
                FROM transactions
                WHERE business_date = ?
                """, row -> archiveJdbcTemplate.update("""
                INSERT INTO transactions_archive (
                    original_id, transaction_ref, source_bank, destination_bank, source_account_no,
                    destination_account_no, amount, currency, status, original_business_date, object_id
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL
                WHERE NOT EXISTS (
                    SELECT 1 FROM transactions_archive WHERE original_id = ?
                )
                """,
                row.get("id"), row.get("transaction_ref"), row.get("source_bank"), row.get("destination_bank"),
                row.get("source_account_no"), row.get("destination_account_no"), row.get("amount"),
                row.get("currency"), row.get("status"), row.get("business_date"), row.get("id")));
    }

    private void archiveTransactionStatusHistory(ArchiveRun run) {
        archiveRows(run, "transaction_status_history", "original_business_date", """
                SELECT id, transaction_ref, from_status, to_status, reason_code, actor, business_date, occurred_at
                FROM transaction_status_history
                WHERE business_date = ?
                """, row -> archiveJdbcTemplate.update("""
                INSERT INTO transaction_status_history_archive (
                    original_id, transaction_ref, from_status, to_status, reason_code,
                    actor, original_business_date, occurred_at
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM transaction_status_history_archive WHERE original_id = ?
                )
                """,
                row.get("id"), row.get("transaction_ref"), row.get("from_status"), row.get("to_status"),
                row.get("reason_code"), row.get("actor"), row.get("business_date"), row.get("occurred_at"),
                row.get("id")));
    }

    private void archiveTransactionEvents(ArchiveRun run) {
        archiveRows(run, "transaction_events", "original_business_date", """
                SELECT id, transaction_ref, event_type, business_date, occurred_at
                FROM transaction_events
                WHERE business_date = ?
                """, row -> archiveJdbcTemplate.update("""
                INSERT INTO transaction_events_archive (
                    original_id, transaction_ref, event_type, original_business_date, occurred_at
                )
                SELECT ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM transaction_events_archive WHERE original_id = ?
                )
                """,
                row.get("id"), row.get("transaction_ref"), row.get("event_type"), row.get("business_date"),
                row.get("occurred_at"), row.get("id")));
    }

    private void archiveIsoMessages(ArchiveRun run) {
        archiveRows(run, "iso_messages", "original_business_date", """
                SELECT id, transaction_ref, inquiry_ref, message_type, direction, validation_status,
                       business_date
                FROM iso_messages
                WHERE business_date = ?
                """, row -> archiveJdbcTemplate.update("""
                INSERT INTO iso_messages_archive (
                    original_id, transaction_ref, inquiry_ref, message_type, direction,
                    status, original_business_date, object_id
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM iso_messages_archive WHERE original_id = ?
                )
                """,
                row.get("id"), row.get("transaction_ref"), row.get("inquiry_ref"), row.get("message_type"),
                row.get("direction"), row.get("validation_status"), row.get("business_date"),
                run.isoMessageObjectIds.get(((Number) row.get("id")).longValue()), row.get("id")));
    }

    private void archiveSettlementItems(ArchiveRun run) {
        archiveRows(run, "settlement_items", "original_settlement_date", """
                SELECT id, cycle_id, bank_code, transaction_ref, direction, amount, currency, settlement_date
                FROM settlement_items
                WHERE settlement_date = ?
                """, row -> archiveJdbcTemplate.update("""
                INSERT INTO settlement_items_archive (
                    original_id, cycle_id, bank_code, transaction_ref, direction, amount,
                    currency, original_settlement_date
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM settlement_items_archive WHERE original_id = ?
                )
                """,
                row.get("id"), row.get("cycle_id"), row.get("bank_code"), row.get("transaction_ref"),
                row.get("direction"), row.get("amount"), row.get("currency"), row.get("settlement_date"),
                row.get("id")));
    }

    private void archiveIsoValidationErrors(ArchiveRun run) {
        archiveRows(run, "iso_validation_errors", "original_business_date", """
                SELECT id, iso_message_id, field_path, error_code, error_message, severity,
                       business_date, created_at
                FROM iso_validation_errors
                WHERE business_date = ?
                """, row -> archiveJdbcTemplate.update("""
                INSERT INTO iso_validation_errors_archive (
                    original_id, iso_message_id, field_path, error_code, error_message,
                    severity, original_business_date, created_at
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM iso_validation_errors_archive WHERE original_id = ?
                )
                """,
                row.get("id"), row.get("iso_message_id"), row.get("field_path"), row.get("error_code"),
                row.get("error_message"), row.get("severity"), row.get("business_date"),
                row.get("created_at"), row.get("id")));
    }

    private void archiveReconciliationItems(ArchiveRun run) {
        archiveRows(run, "reconciliation_items", "original_reconciliation_date", """
                SELECT id, file_id, transaction_ref, amount, currency, match_status, reconciliation_date
                FROM reconciliation_items
                WHERE reconciliation_date = ?
                """, row -> archiveJdbcTemplate.update("""
                INSERT INTO reconciliation_items_archive (
                    original_id, file_id, transaction_ref, amount, currency, match_status,
                    original_reconciliation_date, object_id
                )
                SELECT ?, ?, ?, ?, ?, ?, ?, NULL
                WHERE NOT EXISTS (
                    SELECT 1 FROM reconciliation_items_archive WHERE original_id = ?
                )
                """,
                row.get("id"), row.get("file_id"), row.get("transaction_ref"), row.get("amount"),
                row.get("currency"), row.get("match_status"), row.get("reconciliation_date"), row.get("id")));
    }

    private void archiveIsoPayloads(ArchiveRun run) throws Exception {
        List<Map<String, Object>> payloadRows = primaryJdbcTemplate.queryForList("""
                SELECT id, iso_message_id, payload_type, plain_payload, encrypted_payload,
                       payload_size_bytes, payload_hash, business_date
                FROM iso_message_payloads
                WHERE business_date = ?
                """, run.businessDate);

        for (Map<String, Object> row : payloadRows) {
            Long isoMessageId = ((Number) row.get("iso_message_id")).longValue();
            String payload = row.get("encrypted_payload") != null
                    ? String.valueOf(row.get("encrypted_payload"))
                    : String.valueOf(row.getOrDefault("plain_payload", ""));
            byte[] compressedPayload = gzip(payload.getBytes(StandardCharsets.UTF_8));
            String objectKey = objectKey(run.businessDate, isoMessageId, String.valueOf(row.get("payload_type")));
            putObject(objectKey, compressedPayload, "application/xml", "gzip");
            String checksum = sha256Hex(compressedPayload);
            Long objectId = insertObjectMetadata(
                    objectKey,
                    compressedPayload.length,
                    "application/xml",
                    "gzip",
                    checksum,
                    "ISO_MESSAGE_PAYLOAD",
                    "iso_message_payloads",
                    String.valueOf(row.get("id")),
                    run.businessDate);
            run.isoMessageObjectIds.putIfAbsent(isoMessageId, objectId);
            run.objectCount++;

            primaryJdbcTemplate.update("""
                    UPDATE iso_message_payloads
                    SET stored_in_cold = TRUE,
                        cold_storage_key = ?
                    WHERE id = ? AND business_date = ?
                    """, objectKey, row.get("id"), run.businessDate);
        }
    }

    private void archiveRows(
            ArchiveRun run,
            String tableName,
            String archiveDateColumn,
            String selectSql,
            RowArchiver rowArchiver) {
        String jobRef = "ARCHIVE-" + tableName + "-" + run.businessDate;
        Long jobId = createArchiveJob(jobRef, tableName, run.businessDate);
        List<Map<String, Object>> rows = primaryJdbcTemplate.queryForList(selectSql, run.businessDate);
        long inserted = 0;
        try {
            markJobRunning(jobId);
            for (Map<String, Object> row : rows) {
                inserted += rowArchiver.archive(row);
            }
            long verified = archiveJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName + "_archive WHERE " + archiveDateColumn + " = ?",
                    Long.class,
                    run.businessDate);
            completeArchiveJob(jobId, inserted, verified);
            run.tableCounts.put(tableName, verified);
        } catch (RuntimeException ex) {
            failArchiveJob(jobId, ex.getMessage());
            throw ex;
        }
    }

    private void verifyAndDropPartitions(ArchiveRun run) {
        for (Map.Entry<String, Long> entry : run.tableCounts.entrySet()) {
            String tableName = entry.getKey();
            long sourceCount = primaryJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName + " WHERE " + businessDateColumn(tableName) + " = ?",
                    Long.class,
                    run.businessDate);
            if (sourceCount != entry.getValue()) {
                throw new IllegalStateException("Archive row count mismatch for " + tableName
                        + ": source=" + sourceCount + " archive=" + entry.getValue());
            }
            partitionMaintenanceService.dropPartition(tableName, run.businessDate);
        }
        verifyAndDropIsoPayloadPartition(run);
    }

    private void verifyAndDropIsoPayloadPartition(ArchiveRun run) {
        long sourceCount = primaryJdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM iso_message_payloads
                WHERE business_date = ? AND stored_in_cold = TRUE
                """, Long.class, run.businessDate);
        long totalCount = primaryJdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM iso_message_payloads
                WHERE business_date = ?
                """, Long.class, run.businessDate);
        if (sourceCount != totalCount) {
            throw new IllegalStateException("ISO payload cold-storage verification failed: source="
                    + totalCount + " storedInCold=" + sourceCount);
        }
        partitionMaintenanceService.dropPartition("iso_message_payloads", run.businessDate);
    }

    private void writeManifest(ArchiveRun run) throws Exception {
        String manifestJson = manifestJson(run);
        byte[] manifestBytes = gzip(manifestJson.getBytes(StandardCharsets.UTF_8));
        String manifestKey = "manifests/" + run.businessDate.format(DAY_PATH)
                + "/archive-" + run.businessDate + ".json.gz";
        putObject(manifestKey, manifestBytes, "application/json", "gzip");
        String checksum = sha256Hex(manifestBytes);
        Long objectId = insertObjectMetadata(
                manifestKey,
                manifestBytes.length,
                "application/json",
                "gzip",
                checksum,
                "ARCHIVE_MANIFEST",
                "archive_jobs",
                String.valueOf(run.businessDate),
                run.businessDate);
        archiveJdbcTemplate.update("""
                INSERT INTO object_storage.manifests (
                    archive_job_id, manifest_object_id, manifest_key, source_table,
                    row_count, size_bytes, checksum
                ) VALUES (NULL, ?, ?, ?, ?, ?, ?)
                """, objectId, manifestKey, "daily_archive", run.totalRows(), manifestBytes.length, checksum);
    }

    private Long createArchiveJob(String jobRef, String tableName, LocalDate businessDate) {
        return primaryJdbcTemplate.queryForObject("""
                INSERT INTO archive_jobs (
                    job_ref, job_type, table_name, archive_from, archive_to, status
                ) VALUES (?, 'DAILY_ARCHIVE', ?, ?, ?, 'PENDING')
                ON CONFLICT (job_ref) DO UPDATE
                SET status = CASE
                        WHEN archive_jobs.status = 'COMPLETED' THEN archive_jobs.status
                        ELSE 'PENDING'
                    END,
                    error_message = NULL
                RETURNING id
                """, Long.class, jobRef, tableName, businessDate, businessDate);
    }

    private void markJobRunning(Long jobId) {
        primaryJdbcTemplate.update("""
                UPDATE archive_jobs
                SET status = 'RUNNING', started_at = ?, error_message = NULL
                WHERE id = ?
                """, LocalDateTime.now(), jobId);
    }

    private void completeArchiveJob(Long jobId, long rowsArchived, long rowsVerified) {
        primaryJdbcTemplate.update("""
                UPDATE archive_jobs
                SET status = 'COMPLETED',
                    rows_archived = ?,
                    rows_verified = ?,
                    completed_at = ?
                WHERE id = ?
                """, rowsArchived, rowsVerified, LocalDateTime.now(), jobId);
    }

    private void failArchiveJob(Long jobId, String errorMessage) {
        primaryJdbcTemplate.update("""
                UPDATE archive_jobs
                SET status = 'FAILED', error_message = ?, completed_at = ?
                WHERE id = ?
                """, errorMessage, LocalDateTime.now(), jobId);
    }

    private Long insertObjectMetadata(
            String objectKey,
            long objectSizeBytes,
            String contentType,
            String contentEncoding,
            String payloadHash,
            String objectPurpose,
            String sourceTable,
            String referenceId,
            LocalDate businessDate) {
        return archiveJdbcTemplate.queryForObject("""
                INSERT INTO object_storage.objects (
                    storage_bucket, object_key, object_size_bytes, content_type,
                    content_encoding, payload_hash, object_purpose, source_table,
                    reference_id, business_date, retention_until
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (storage_bucket, object_key) DO UPDATE
                SET object_size_bytes = EXCLUDED.object_size_bytes,
                    content_type = EXCLUDED.content_type,
                    content_encoding = EXCLUDED.content_encoding,
                    payload_hash = EXCLUDED.payload_hash,
                    object_purpose = EXCLUDED.object_purpose,
                    source_table = EXCLUDED.source_table,
                    reference_id = EXCLUDED.reference_id,
                    business_date = EXCLUDED.business_date,
                    retention_until = EXCLUDED.retention_until
                RETURNING id
                """, Long.class,
                archiveProperties.getObjectStorage().getBucket(),
                objectKey,
                objectSizeBytes,
                contentType,
                contentEncoding,
                payloadHash,
                objectPurpose,
                sourceTable,
                referenceId,
                businessDate,
                businessDate.plusYears(archiveProperties.getObjectStorage().getRetentionYears()));
    }

    private void ensureBucket() throws Exception {
        String bucket = archiveProperties.getObjectStorage().getBucket();
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private void putObject(String objectKey, byte[] payload, String contentType, String contentEncoding) throws Exception {
        Map<String, String> headers = Map.of("Content-Encoding", contentEncoding);
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(archiveProperties.getObjectStorage().getBucket())
                .object(objectKey)
                .contentType(contentType)
                .headers(headers)
                .stream(new ByteArrayInputStream(payload), payload.length, -1)
                .build());
    }

    private static byte[] gzip(byte[] payload) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(payload);
        }
        return output.toByteArray();
    }

    private static String sha256Hex(byte[] payload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(payload));
    }

    private String objectKey(LocalDate businessDate, Long isoMessageId, String payloadType) {
        return "iso/" + businessDate.format(DAY_PATH)
                + "/message/" + isoMessageId
                + "/" + payloadType.toLowerCase() + ".xml.gz";
    }

    private static String businessDateColumn(String tableName) {
        return switch (tableName) {
            case "settlement_items" -> "settlement_date";
            case "reconciliation_items" -> "reconciliation_date";
            default -> "business_date";
        };
    }

    private static String manifestJson(ArchiveRun run) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"businessDate\":\"").append(run.businessDate).append("\",");
        builder.append("\"objectCount\":").append(run.objectCount).append(",");
        builder.append("\"tables\":{");
        boolean first = true;
        for (Map.Entry<String, Long> entry : run.tableCounts.entrySet()) {
            if (!first) {
                builder.append(",");
            }
            builder.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        builder.append("}}");
        return builder.toString();
    }

    @FunctionalInterface
    private interface RowArchiver {
        int archive(Map<String, Object> row);
    }

    private static final class ArchiveRun {
        private final LocalDate businessDate;
        private final Map<String, Long> tableCounts = new LinkedHashMap<>();
        private final Map<Long, Long> isoMessageObjectIds = new LinkedHashMap<>();
        private long objectCount;

        private ArchiveRun(LocalDate businessDate) {
            this.businessDate = businessDate;
        }

        private long totalRows() {
            return tableCounts.values().stream().mapToLong(Long::longValue).sum();
        }
    }
}
