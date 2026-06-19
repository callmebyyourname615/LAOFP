package com.example.switching.liquidity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.liquidity.dto.PoolBalance;
import com.example.switching.liquidity.exception.InsufficientPoolBalanceException;
import com.example.switching.liquidity.service.PoolService;

class PoolServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String PSP_ID = "POOL_TEST_PSP";

    @Autowired PoolService poolService;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM participants WHERE bank_code = ?", PSP_ID);
    }

    @Test
    void holdConfirmAndRelease_keepBalancesConsistentAndHoldIsIdempotent() {
        seedPool(new BigDecimal("1000.0000"));

        PoolBalance afterHold = poolService.holdFunds(PSP_ID, "TXN-HOLD-1", new BigDecimal("250.0000"));
        assertMoney("1000.0000", afterHold.balance());
        assertMoney("250.0000", afterHold.heldAmount());
        assertMoney("750.0000", afterHold.availableBalance());

        PoolBalance afterDuplicateHold = poolService.holdFunds(PSP_ID, "TXN-HOLD-1", new BigDecimal("250.0000"));
        assertMoney("1000.0000", afterDuplicateHold.balance());
        assertMoney("250.0000", afterDuplicateHold.heldAmount());
        assertMoney("750.0000", afterDuplicateHold.availableBalance());
        assertEquals(1, countTransactions("TXN-HOLD-1", "HOLD"));

        PoolBalance afterConfirm = poolService.confirmHold("TXN-HOLD-1");
        assertMoney("750.0000", afterConfirm.balance());
        assertMoney("0.0000", afterConfirm.heldAmount());
        assertMoney("750.0000", afterConfirm.availableBalance());

        poolService.holdFunds(PSP_ID, "TXN-HOLD-2", new BigDecimal("100.0000"));
        PoolBalance afterRelease = poolService.releaseHold("TXN-HOLD-2");
        assertMoney("750.0000", afterRelease.balance());
        assertMoney("0.0000", afterRelease.heldAmount());
        assertMoney("750.0000", afterRelease.availableBalance());
    }

    @Test
    void holdFunds_rejectsInsufficientBalanceAndLeavesBalanceUnchanged() {
        seedPool(new BigDecimal("500.0000"));

        assertThrows(InsufficientPoolBalanceException.class,
                () -> poolService.holdFunds(PSP_ID, "TXN-TOO-BIG", new BigDecimal("600.0000")));

        PoolBalance balance = poolService.getAvailableBalance(PSP_ID);
        assertMoney("500.0000", balance.balance());
        assertMoney("0.0000", balance.heldAmount());
        assertMoney("500.0000", balance.availableBalance());
        assertEquals(0, countTransactions("TXN-TOO-BIG", "HOLD"));
    }

    @Test
    void topUp_increasesBalanceAndHistoryReturnsLatestOperations() {
        seedPool(new BigDecimal("0.0000"));

        var topUp = poolService.topUp(
                PSP_ID,
                new BigDecimal("1500.0000"),
                "TOPUP-REF-1",
                "TEST-ACTOR");

        assertEquals("COMPLETED", topUp.status());
        assertMoney("1500.0000", topUp.balance().balance());
        assertMoney("0.0000", topUp.balance().heldAmount());
        assertMoney("1500.0000", topUp.balance().availableBalance());

        poolService.holdFunds(PSP_ID, "TXN-AFTER-TOPUP", new BigDecimal("200.0000"));

        var history = poolService.history(PSP_ID, 10);
        assertEquals("HOLD", history.get(0).operation());
        assertEquals("TOPUP", history.get(1).operation());
        assertEquals("TOPUP-REF-1", history.get(1).txnId());
        assertEquals("TEST-ACTOR", history.get(1).initiatedBy());
    }

    @Test
    void concurrentHolds_doNotOversellAvailableBalance() throws Exception {
        seedPool(new BigDecimal("5000.0000"));

        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                int index = i;
                tasks.add(() -> {
                    try {
                        poolService.holdFunds(PSP_ID, "TXN-CONC-" + index, new BigDecimal("100.0000"));
                        return true;
                    } catch (InsufficientPoolBalanceException ex) {
                        return false;
                    }
                });
            }

            List<Future<Boolean>> results = executor.invokeAll(tasks);
            long successCount = results.stream().filter(this::futureValue).count();
            long rejectedCount = results.size() - successCount;

            assertEquals(50, successCount);
            assertEquals(10, rejectedCount);

            PoolBalance balance = poolService.getAvailableBalance(PSP_ID);
            assertMoney("5000.0000", balance.balance());
            assertMoney("5000.0000", balance.heldAmount());
            assertMoney("0.0000", balance.availableBalance());
            assertTrue(balance.availableBalance().signum() >= 0);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean futureValue(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private void seedPool(BigDecimal balance) {
        jdbcTemplate.update("""
                INSERT INTO participants (bank_code, bank_name, status, participant_type, country, currency, created_at)
                VALUES (?, ?, 'ACTIVE', 'DIRECT', 'LA', 'LAK', NOW())
                ON CONFLICT (bank_code) DO NOTHING
                """, PSP_ID, "Pool Test PSP");
        jdbcTemplate.update("""
                INSERT INTO psp_pools (psp_id, balance, held_amount, currency, minimum_balance)
                VALUES (?, ?, 0, 'LAK', 100000000)
                ON CONFLICT (psp_id) DO UPDATE
                    SET balance = EXCLUDED.balance,
                        held_amount = 0,
                        last_alert_sent_at = NULL,
                        last_updated_at = NOW()
                """, PSP_ID, balance);
    }

    private Integer countTransactions(String txnId, String operation) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM pool_transactions
                 WHERE txn_id = ?
                   AND operation = ?
                """, Integer.class, txnId, operation);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }
}
