package com.example.switching.fees;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TariffQueryService {

    private final JdbcTemplate jdbc;

    public TariffQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TariffPlanSummaryResponse> list() {
        return jdbc.query("""
                SELECT tv.id,
                       tp.plan_code,
                       tv.version_no,
                       tv.status,
                       tv.valid_from,
                       count(tr.id)::int AS rule_count
                  FROM tariff_version tv
                  JOIN tariff_plan tp ON tp.id = tv.plan_id
                  LEFT JOIN tariff_rule tr ON tr.tariff_version_id = tv.id
                 GROUP BY tv.id, tp.plan_code, tv.version_no, tv.status, tv.valid_from
                 ORDER BY tp.plan_code ASC, tv.version_no DESC
                """, (rs, rowNum) -> new TariffPlanSummaryResponse(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("plan_code"),
                rs.getInt("version_no"),
                rs.getString("status"),
                rs.getObject("valid_from", java.time.OffsetDateTime.class),
                rs.getInt("rule_count")
        ));
    }

    public TariffPlanSummaryResponse get(UUID versionId) {
        return jdbc.queryForObject("""
                SELECT tv.id,
                       tp.plan_code,
                       tv.version_no,
                       tv.status,
                       tv.valid_from,
                       count(tr.id)::int AS rule_count
                  FROM tariff_version tv
                  JOIN tariff_plan tp ON tp.id = tv.plan_id
                  LEFT JOIN tariff_rule tr ON tr.tariff_version_id = tv.id
                 WHERE tv.id = ?
                 GROUP BY tv.id, tp.plan_code, tv.version_no, tv.status, tv.valid_from
                """, (rs, rowNum) -> new TariffPlanSummaryResponse(
                rs.getObject("id", java.util.UUID.class),
                rs.getString("plan_code"),
                rs.getInt("version_no"),
                rs.getString("status"),
                rs.getObject("valid_from", java.time.OffsetDateTime.class),
                rs.getInt("rule_count")
        ), versionId);
    }
}
