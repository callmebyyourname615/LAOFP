package com.example.switching.phaseii;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.switching.AbstractIntegrationTest;
import com.example.switching.crossborder.service.RailMessageJournalService;

class RailMessageJournalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RailMessageJournalService journal;

    @BeforeEach
    void cleanJournal() {
        jdbc.update("DELETE FROM cross_border_rail_reconciliation");
        jdbc.update("DELETE FROM cross_border_rail_message");
    }

    @Test
    void identicalReplayReturnsSameMessageButChangedPayloadIsRejected() {
        UUID first = journal.recordOutbound(
                "PROMPTPAY",
                "EXT-001",
                "INT-001",
                "PAYMENT",
                Map.of("amount", "100.0000", "currency", "LAK"));
        UUID replay = journal.recordOutbound(
                "PROMPTPAY",
                "EXT-001",
                "INT-001",
                "PAYMENT",
                Map.of("amount", "100.0000", "currency", "LAK"));

        assertEquals(first, replay);
        assertThrows(IllegalStateException.class, () -> journal.recordOutbound(
                "PROMPTPAY",
                "EXT-001",
                "INT-001",
                "PAYMENT",
                Map.of("amount", "999.0000", "currency", "LAK")));
    }
}
