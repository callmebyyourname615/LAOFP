package com.example.switching.settlement.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.switching.settlement.dto.SettlementTimelineItemResponse;
import com.example.switching.settlement.dto.SettlementTimelineResponse;
import com.example.switching.settlement.entity.SettlementCycleEntity;

@Service
public class SettlementTimelineService {

    private static final List<String> SETTLEMENT_EVENTS = List.of(
            "SETTLEMENT_CYCLE_OPENED",
            "SETTLEMENT_BATCH_COMPLETED",
            "SETTLEMENT_CYCLE_CLOSED",
            "SETTLEMENT_INSTRUCTIONS_GENERATED",
            "SETTLEMENT_INSTRUCTION_APPROVED",
            "SETTLEMENT_INSTRUCTION_REJECTED",
            "SETTLEMENT_INSTRUCTION_RTGS_FILE_PREPARED",
            "SETTLEMENT_INSTRUCTION_RTGS_PORTAL_UPLOADED",
            "SETTLEMENT_INSTRUCTION_SENT_RTGS",
            "SETTLEMENT_INSTRUCTION_RTGS_FAILED",
            "SETTLEMENT_INSTRUCTION_RTGS_ERROR",
            "SETTLEMENT_INSTRUCTION_RTGS_CALLBACK",
            "SETTLEMENT_NET_POSITIONS_APPLIED",
            "SETTLEMENT_CYCLE_SETTLED",
            "SETTLEMENT_REPORT_GENERATED");

    private final SettlementCycleService cycleService;
    private final JdbcTemplate jdbc;

    public SettlementTimelineService(SettlementCycleService cycleService, JdbcTemplate jdbc) {
        this.cycleService = cycleService;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public SettlementTimelineResponse timeline(String cycleRef) {
        SettlementCycleEntity cycle = cycleService.requireCycle(cycleRef);
        List<String> refs = new ArrayList<>();
        refs.add(cycleRef);
        refs.addAll(instructionRefs(cycle.getId()));

        String placeholders = String.join(",", refs.stream().map(v -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.addAll(SETTLEMENT_EVENTS);
        args.addAll(refs);
        args.add("%\"cycleRef\":\"" + cycleRef + "\"%");

        String sql = """
                SELECT id, event_type, reference_type, reference_id, actor, payload, trace_id, created_at
                  FROM audit_logs
                 WHERE event_type = ANY (?::varchar[])
                   AND (
                        reference_id IN (__REFS__)
                        OR payload LIKE ?
                   )
                 ORDER BY created_at ASC, id ASC
                """.replace("__REFS__", placeholders);

        List<SettlementTimelineItemResponse> items = jdbc.query(
                sql,
                ps -> {
                    ps.setArray(1, ps.getConnection().createArrayOf("varchar", SETTLEMENT_EVENTS.toArray()));
                    int index = 2;
                    for (String ref : refs) {
                        ps.setString(index++, ref);
                    }
                    ps.setString(index, "%\"cycleRef\":\"" + cycleRef + "\"%");
                },
                (rs, rowNum) -> map(rs));

        return new SettlementTimelineResponse(cycleRef, items.size(), items);
    }

    private List<String> instructionRefs(Long cycleId) {
        return jdbc.queryForList(
                "SELECT instruction_ref FROM settlement_instructions WHERE cycle_id = ? ORDER BY instruction_ref",
                String.class,
                cycleId);
    }

    private SettlementTimelineItemResponse map(ResultSet rs) throws SQLException {
        return new SettlementTimelineItemResponse(
                rs.getLong("id"),
                rs.getString("event_type"),
                rs.getString("reference_type"),
                rs.getString("reference_id"),
                rs.getString("actor"),
                rs.getString("payload"),
                rs.getString("trace_id"),
                time(rs, "created_at"));
    }

    private LocalDateTime time(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
