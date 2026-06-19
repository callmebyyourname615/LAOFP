package com.example.switching.maintenance.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.config.ArchiveProperties;
import com.example.switching.compliance.legalhold.service.LegalHoldService;

import org.springframework.jdbc.core.JdbcTemplate;

@Service
@Profile("!migration")
public class PartitionMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceService.class);
    private static final DateTimeFormatter PARTITION_SUFFIX = DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate jdbcTemplate;
    private final ArchiveProperties archiveProperties;
    private final SchedulerLockService schedulerLockService;
    private final LegalHoldService legalHoldService;

    private final List<String> partitionedTables = List.of(
            "payment_flows",
            "inquiries",
            "transactions",
            "transaction_status_history",
            "transaction_events",
            "iso_messages",
            "iso_message_payloads",
            "iso_validation_errors",
            "settlement_items",
            "reconciliation_items"
    );

    public PartitionMaintenanceService(
            JdbcTemplate jdbcTemplate,
            ArchiveProperties archiveProperties,
            SchedulerLockService schedulerLockService,
            LegalHoldService legalHoldService) {
        this.jdbcTemplate = jdbcTemplate;
        this.archiveProperties = archiveProperties;
        this.schedulerLockService = schedulerLockService;
        this.legalHoldService = legalHoldService;
    }

    @Scheduled(cron = "${switching.archive.partition-cron:0 5 0 * * *}")
    public void runDailyPartitionMaintenance() {
        if (!archiveProperties.isPartitionMaintenanceEnabled()) {
            return;
        }
        if (!schedulerLockService.acquire("partition-maintenance", 30)) {
            log.debug("Partition maintenance skipped because another instance owns the lock");
            return;
        }
        try {
            createFuturePartitions(LocalDate.now(), archiveProperties.getPartitionForwardDays());
        } finally {
            schedulerLockService.release("partition-maintenance");
        }
    }

    public int createFuturePartitions(LocalDate fromDate, int forwardDays) {
        int createdOrEnsured = 0;
        for (int d = 0; d <= forwardDays; d++) {
            LocalDate targetDate = fromDate.plusDays(d);
            for (String tableName : partitionedTables) {
                ensurePartition(tableName, targetDate);
                createdOrEnsured++;
            }
        }
        log.info("Partition maintenance ensured {} partition slots through {} day(s) ahead",
                createdOrEnsured, forwardDays);
        return createdOrEnsured;
    }

    @Transactional
    public void dropPartition(String tableName, LocalDate partitionDate) {
        if (!partitionedTables.contains(tableName)) {
            throw new IllegalArgumentException("table is not an approved partition target: " + tableName);
        }
        String partitionName = partitionName(tableName, partitionDate);
        Optional<String> blockingHold = legalHoldService.blockingHoldRef(tableName, partitionDate);
        if (blockingHold.isPresent()) {
            logPartitionOperation(tableName, partitionName, "DROP_BLOCKED_LEGAL_HOLD", partitionDate, false,
                    "legal hold " + blockingHold.get());
            logRetentionExecution(tableName, partitionDate, "BLOCKED", blockingHold.get(),
                    "partition drop blocked by legal hold");
            throw new IllegalStateException("legal hold blocks partition drop: " + partitionName);
        }
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + quoteIdentifier(partitionName));
            logPartitionOperation(tableName, partitionName, "DROP", partitionDate, true, null);
            logRetentionExecution(tableName, partitionDate, "SUCCEEDED", null,
                    "archived partition dropped");
            log.info("Dropped archived partition {} for table {}", partitionName, tableName);
        } catch (RuntimeException ex) {
            logPartitionOperation(tableName, partitionName, "DROP", partitionDate, false, ex.getMessage());
            logRetentionExecution(tableName, partitionDate, "FAILED", null, truncate(ex.getMessage(), 1000));
            throw ex;
        }
    }

    private void ensurePartition(String tableName, LocalDate partitionDate) {
        String partitionName = partitionName(tableName, partitionDate);
        LocalDate nextDate = partitionDate.plusDays(1);
        String sql = """
                CREATE TABLE IF NOT EXISTS %s PARTITION OF %s
                FOR VALUES FROM ('%s') TO ('%s')
                """.formatted(
                quoteIdentifier(partitionName),
                quoteIdentifier(tableName),
                partitionDate,
                nextDate);
        try {
            jdbcTemplate.execute(sql);
            logPartitionOperation(tableName, partitionName, "CREATE", partitionDate, true, null);
        } catch (RuntimeException ex) {
            logPartitionOperation(tableName, partitionName, "CREATE", partitionDate, false, ex.getMessage());
            throw ex;
        }
    }

    private void logPartitionOperation(
            String tableName,
            String partitionName,
            String operation,
            LocalDate partitionDate,
            boolean success,
            String errorMessage) {
        jdbcTemplate.update("""
                INSERT INTO partition_maintenance_logs (
                    table_name, partition_name, operation, partition_date, success, error_message
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, tableName, partitionName, operation, partitionDate, success, errorMessage);
    }


    private void logRetentionExecution(String tableName,
                                       LocalDate businessDate,
                                       String status,
                                       String legalHoldRef,
                                       String details) {
        jdbcTemplate.update("""
                INSERT INTO retention_execution_log (
                    policy_name, target_table, business_date, action, status,
                    affected_rows, legal_hold_ref, details
                ) VALUES (?, ?, ?, 'DROP_PARTITION', ?, NULL, ?, ?)
                """, "ARCHIVE_PARTITION_RETENTION", tableName, businessDate,
                status, legalHoldRef, truncate(details, 1000));
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static String partitionName(String tableName, LocalDate partitionDate) {
        return tableName + "_" + partitionDate.format(PARTITION_SUFFIX);
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
