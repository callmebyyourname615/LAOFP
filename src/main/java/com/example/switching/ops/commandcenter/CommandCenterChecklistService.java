package com.example.switching.ops.commandcenter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

@Service
public class CommandCenterChecklistService {
    private final JdbcTemplate jdbcTemplate;

    public CommandCenterChecklistService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> dailyReadiness(LocalDate businessDate) {
        return jdbcTemplate.queryForMap("""
                SELECT ?::date AS business_date,
                       (SELECT count(*) FROM reconciliation_control_run WHERE business_date = ? AND status = 'EXCEPTION') AS reconciliation_exceptions,
                       (SELECT count(*) FROM ops_control_room_task t JOIN ops_daily_control_room r ON r.id = t.control_room_id
                         WHERE r.business_date = ? AND t.status <> 'DONE') AS open_tasks,
                       (SELECT count(*) FROM fraud_velocity_decision WHERE created_at::date = ? AND decision IN ('HOLD','REJECT')) AS blocked_transactions
                """, businessDate, businessDate, businessDate, businessDate);
    }
}
