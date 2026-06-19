package com.example.switching.liquidity.service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.switching.liquidity.dto.PoolBalance;
import com.example.switching.liquidity.exception.InsufficientPoolBalanceException;
import com.example.switching.liquidity.exception.PoolHoldNotFoundException;

@Service
public class PoolService {

    private static final Logger log = LoggerFactory.getLogger(PoolService.class);

    private final JdbcTemplate jdbcTemplate;

    public PoolService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public PoolBalance holdFunds(String pspId, String txnId, BigDecimal amount) {
        requirePositive(amount, "amount");
        String normalizedPspId = requireText(pspId, "pspId");
        String normalizedTxnId = requireText(txnId, "txnId");

        Optional<PoolTransaction> existingHold = findTransaction(normalizedTxnId, "HOLD");
        if (existingHold.isPresent()) {
            PoolTransaction hold = existingHold.get();
            if (hold.amount().compareTo(amount) != 0) {
                throw new IllegalArgumentException("Existing hold amount differs for transaction " + normalizedTxnId);
            }
            return getAvailableBalance(normalizedPspId);
        }

        PoolRow pool = lockPool(normalizedPspId);
        if (pool == null) {
            // No pool row configured for this PSP — skip enforcement (pool not yet set up)
            log.debug("No pool row found for psp={}, skipping hold for txnId={}", normalizedPspId, normalizedTxnId);
            return zeroBalance(normalizedPspId);
        }
        if (pool.availableBalance().compareTo(amount) < 0) {
            throw new InsufficientPoolBalanceException(normalizedPspId, amount, pool.availableBalance());
        }

        BigDecimal heldAfter = pool.heldAmount().add(amount);
        jdbcTemplate.update("""
                UPDATE psp_pools
                   SET held_amount = ?,
                       last_updated_at = NOW()
                 WHERE pool_id = ?
                """, heldAfter, pool.poolId());

        insertTransaction(pool, normalizedTxnId, "HOLD", amount, pool.balance(), heldAfter);
        return getAvailableBalance(normalizedPspId);
    }

    @Transactional
    public PoolBalance confirmHold(String txnId) {
        String normalizedTxnId = requireText(txnId, "txnId");
        Optional<PoolTransaction> holdOpt = findTransaction(normalizedTxnId, "HOLD");
        if (holdOpt.isEmpty()) {
            log.warn("confirmHold: no HOLD record found for txnId={}, skipping (pool not configured or hold not placed)",
                    normalizedTxnId);
            return zeroBalance(txnId);
        }
        PoolTransaction hold = holdOpt.get();

        Optional<PoolTransaction> existingConfirm = findTransaction(normalizedTxnId, "CONFIRM");
        if (existingConfirm.isPresent()) {
            return getAvailableBalance(hold.pspId());
        }
        if (findTransaction(normalizedTxnId, "RELEASE").isPresent()) {
            log.warn("confirmHold: txnId={} was already released, skipping confirm", normalizedTxnId);
            return getAvailableBalance(hold.pspId());
        }

        PoolRow pool = lockPool(hold.pspId());
        BigDecimal balanceAfter = pool.balance().subtract(hold.amount());
        BigDecimal heldAfter = pool.heldAmount().subtract(hold.amount());
        if (heldAfter.signum() < 0 || balanceAfter.signum() < 0) {
            throw new IllegalStateException("Pool hold state is inconsistent for transaction " + normalizedTxnId);
        }

        jdbcTemplate.update("""
                UPDATE psp_pools
                   SET balance = ?,
                       held_amount = ?,
                       last_updated_at = NOW()
                 WHERE pool_id = ?
                """, balanceAfter, heldAfter, pool.poolId());

        insertTransaction(pool, normalizedTxnId, "CONFIRM", hold.amount(), balanceAfter, heldAfter);
        return getAvailableBalance(hold.pspId());
    }

    @Transactional
    public PoolBalance releaseHold(String txnId) {
        String normalizedTxnId = requireText(txnId, "txnId");
        Optional<PoolTransaction> holdOpt = findTransaction(normalizedTxnId, "HOLD");
        if (holdOpt.isEmpty()) {
            log.warn("releaseHold: no HOLD record found for txnId={}, skipping (pool not configured or hold not placed)",
                    normalizedTxnId);
            return zeroBalance(txnId);
        }
        PoolTransaction hold = holdOpt.get();

        Optional<PoolTransaction> existingRelease = findTransaction(normalizedTxnId, "RELEASE");
        if (existingRelease.isPresent()) {
            return getAvailableBalance(hold.pspId());
        }
        if (findTransaction(normalizedTxnId, "CONFIRM").isPresent()) {
            log.warn("releaseHold: txnId={} was already confirmed, skipping release", normalizedTxnId);
            return getAvailableBalance(hold.pspId());
        }

        PoolRow pool = lockPool(hold.pspId());
        BigDecimal heldAfter = pool.heldAmount().subtract(hold.amount());
        if (heldAfter.signum() < 0) {
            throw new IllegalStateException("Pool hold state is inconsistent for transaction " + normalizedTxnId);
        }

        jdbcTemplate.update("""
                UPDATE psp_pools
                   SET held_amount = ?,
                       last_updated_at = NOW()
                 WHERE pool_id = ?
                """, heldAfter, pool.poolId());

        insertTransaction(pool, normalizedTxnId, "RELEASE", hold.amount(), pool.balance(), heldAfter);
        return getAvailableBalance(hold.pspId());
    }

    @Transactional(readOnly = true)
    public PoolBalance getAvailableBalance(String pspId) {
        return jdbcTemplate.queryForObject("""
                SELECT psp_id, balance, held_amount, available_balance, currency, minimum_balance, last_updated_at
                  FROM psp_pools
                 WHERE psp_id = ?
                """, (rs, rowNum) -> new PoolBalance(
                rs.getString("psp_id"),
                rs.getBigDecimal("balance"),
                rs.getBigDecimal("held_amount"),
                rs.getBigDecimal("available_balance"),
                rs.getString("currency"),
                rs.getBigDecimal("minimum_balance"),
                rs.getTimestamp("last_updated_at").toLocalDateTime()), requireText(pspId, "pspId"));
    }

    @Transactional
    public PoolTopUpResult topUp(String pspId, BigDecimal amount, String reference, String initiatedBy) {
        requirePositive(amount, "amount");
        String normalizedPspId = requireText(pspId, "pspId");
        String normalizedReference = requireText(reference, "reference");
        String actor = StringUtils.hasText(initiatedBy) ? initiatedBy.trim() : "SYSTEM";

        PoolRow pool = lockPool(normalizedPspId);
        BigDecimal balanceAfter = pool.balance().add(amount);

        jdbcTemplate.update("""
                UPDATE psp_pools
                   SET balance = ?,
                       last_updated_at = NOW()
                 WHERE pool_id = ?
                """, balanceAfter, pool.poolId());

        Long topUpId = insertTransaction(
                pool,
                normalizedReference,
                "TOPUP",
                amount,
                balanceAfter,
                pool.heldAmount(),
                actor);
        return new PoolTopUpResult(topUpId, normalizedReference, "COMPLETED", getAvailableBalance(normalizedPspId));
    }

    @Transactional(readOnly = true)
    public List<PoolTransactionHistoryItem> history(String pspId, int limit) {
        String normalizedPspId = requireText(pspId, "pspId");
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query("""
                SELECT pt.pool_txn_id, pt.txn_id, pt.operation, pt.amount,
                       pt.balance_before, pt.held_before, pt.balance_after, pt.held_after,
                       pt.occurred_at, pt.initiated_by
                  FROM pool_transactions pt
                  JOIN psp_pools pp ON pp.pool_id = pt.pool_id
                 WHERE pp.psp_id = ?
                 ORDER BY pt.pool_txn_id DESC
                 LIMIT ?
                """, (rs, rowNum) -> new PoolTransactionHistoryItem(
                rs.getLong("pool_txn_id"),
                rs.getString("txn_id"),
                rs.getString("operation"),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("balance_before"),
                rs.getBigDecimal("held_before"),
                rs.getBigDecimal("balance_after"),
                rs.getBigDecimal("held_after"),
                rs.getTimestamp("occurred_at").toLocalDateTime(),
                rs.getString("initiated_by")), normalizedPspId, safeLimit);
    }

    private PoolRow lockPool(String pspId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT pool_id, psp_id, balance, held_amount, available_balance
                      FROM psp_pools
                     WHERE psp_id = ?
                     FOR UPDATE
                    """, this::mapPoolRow, pspId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    /** Sentinel returned when no pool row exists for the PSP (enforcement not configured). */
    private PoolBalance zeroBalance(String pspId) {
        return new PoolBalance(pspId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "LAK", BigDecimal.ZERO, java.time.LocalDateTime.now());
    }

    private PoolRow mapPoolRow(ResultSet rs, int rowNum) throws SQLException {
        return new PoolRow(
                rs.getLong("pool_id"),
                rs.getString("psp_id"),
                rs.getBigDecimal("balance"),
                rs.getBigDecimal("held_amount"),
                rs.getBigDecimal("available_balance"));
    }

    private Optional<PoolTransaction> findTransaction(String txnId, String operation) {
        List<PoolTransaction> rows = jdbcTemplate.query("""
                SELECT pt.pool_txn_id, pp.psp_id, pt.amount
                  FROM pool_transactions pt
                  JOIN psp_pools pp ON pp.pool_id = pt.pool_id
                 WHERE pt.txn_id = ?
                   AND pt.operation = ?
                 ORDER BY pt.pool_txn_id DESC
                 LIMIT 1
                """, (rs, rowNum) -> new PoolTransaction(
                rs.getLong("pool_txn_id"),
                rs.getString("psp_id"),
                rs.getBigDecimal("amount")), txnId, operation);
        return rows.stream().findFirst();
    }

    private void insertTransaction(PoolRow before, String txnId, String operation, BigDecimal amount,
            BigDecimal balanceAfter, BigDecimal heldAfter) {
        insertTransaction(before, txnId, operation, amount, balanceAfter, heldAfter, "SYSTEM");
    }

    private Long insertTransaction(PoolRow before, String txnId, String operation, BigDecimal amount,
            BigDecimal balanceAfter, BigDecimal heldAfter, String initiatedBy) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO pool_transactions
                        (pool_id, txn_id, operation, amount, balance_before, held_before,
                         balance_after, held_after, initiated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[] {"pool_txn_id"});
            ps.setLong(1, before.poolId());
            ps.setString(2, txnId);
            ps.setString(3, operation);
            ps.setBigDecimal(4, amount);
            ps.setBigDecimal(5, before.balance());
            ps.setBigDecimal(6, before.heldAmount());
            ps.setBigDecimal(7, balanceAfter);
            ps.setBigDecimal(8, heldAfter);
            ps.setString(9, initiatedBy);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private void requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private record PoolRow(
            Long poolId,
            String pspId,
            BigDecimal balance,
            BigDecimal heldAmount,
            BigDecimal availableBalance) {
    }

    private record PoolTransaction(
            Long poolTxnId,
            String pspId,
            BigDecimal amount) {
    }

    public record PoolTopUpResult(
            Long topUpId,
            String reference,
            String status,
            PoolBalance balance) {
    }

    public record PoolTransactionHistoryItem(
            Long poolTxnId,
            String txnId,
            String operation,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal heldBefore,
            BigDecimal balanceAfter,
            BigDecimal heldAfter,
            LocalDateTime occurredAt,
            String initiatedBy) {
    }
}
