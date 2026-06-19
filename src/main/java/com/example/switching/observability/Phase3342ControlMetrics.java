package com.example.switching.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Profile("!migration")
public class Phase3342ControlMetrics {
    private final JdbcTemplate jdbc;
    private final Map<String,AtomicLong> gauges=new LinkedHashMap<>();

    public Phase3342ControlMetrics(JdbcTemplate jdbc,MeterRegistry registry){
        this.jdbc=jdbc;
        for(String name:queries().keySet()){
            AtomicLong value=new AtomicLong(); gauges.put(name,value);
            Gauge.builder(name,value,AtomicLong::get).register(registry);
        }
    }

    @Scheduled(fixedDelayString="${switching.observability.phase33-42-refresh:PT60S}")
    public void refresh(){
        queries().forEach((name,sql)->{
            Long value=jdbc.queryForObject(sql,Long.class);
            gauges.get(name).set(value==null?0L:value);
        });
    }

    private static Map<String,String> queries(){
        Map<String,String> q=new LinkedHashMap<>();
        q.put("switching.ledger.unbalanced.posted","SELECT count(*) FROM (SELECT j.id FROM control_journal j LEFT JOIN control_journal_entry e ON e.journal_id=j.id WHERE j.status='POSTED' GROUP BY j.id HAVING coalesce(sum(e.amount) FILTER (WHERE e.side='DEBIT'),0)<>coalesce(sum(e.amount) FILTER (WHERE e.side='CREDIT'),0)) x");
        q.put("switching.liquidity.breaches.open","SELECT count(*) FROM liquidity_control_breach WHERE resolved_at IS NULL");
        q.put("switching.fx.pairs.without.valid.rate","SELECT count(*) FROM fx_governance_policy p WHERE p.enabled AND NOT EXISTS (SELECT 1 FROM governed_fx_rate_publication r WHERE r.currency_pair=p.currency_pair AND r.status='APPROVED' AND r.valid_until>now())");
        q.put("switching.participant.certificates.expiring.30d","SELECT count(*) FROM participant_certificate WHERE status IN ('ACTIVE','OVERLAP') AND not_after<=now()+interval '30 days'");
        q.put("switching.regulatory.reports.overdue","SELECT count(*) FROM regulatory_report_run WHERE status NOT IN ('ACKNOWLEDGED','REJECTED') AND period_end<current_date");
        q.put("switching.notifications.dead.last30m","SELECT count(*) FROM notification_delivery WHERE status='DEAD' AND created_at>=now()-interval '30 minutes'");
        q.put("switching.synthetic.failures.last10m","SELECT count(*) FROM synthetic_probe_execution WHERE status IN ('FAIL','TIMEOUT','CLEANUP_FAILED') AND started_at>=now()-interval '10 minutes'");
        q.put("switching.incident.actions.overdue","SELECT count(*) FROM corrective_action WHERE status NOT IN ('DONE','RISK_ACCEPTED') AND due_at<now()");
        return Map.copyOf(q);
    }
}
