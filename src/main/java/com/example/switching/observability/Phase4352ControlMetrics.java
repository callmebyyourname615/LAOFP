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
public class Phase4352ControlMetrics {
    private final JdbcTemplate jdbc;
    private final Map<String,AtomicLong> gauges=new LinkedHashMap<>();

    public Phase4352ControlMetrics(JdbcTemplate jdbc,MeterRegistry registry){
        this.jdbc=jdbc;
        queries().forEach((name,sql)->{
            AtomicLong value=new AtomicLong(); gauges.put(name,value);
            Gauge.builder(name,value,AtomicLong::get).register(registry);
        });
    }

    @Scheduled(fixedDelayString="${switching.observability.phase43-52-refresh:PT60S}")
    public void refresh(){queries().forEach((name,sql)->{Long value=jdbc.queryForObject(sql,Long.class);gauges.get(name).set(value==null?0:value);});}

    private static Map<String,String> queries(){
        Map<String,String> q=new LinkedHashMap<>();
        q.put("switching.limits.denied.last15m","SELECT count(*) FROM transaction_limit_decision_audit WHERE decision='DENY' AND decided_at>=now()-interval '15 minutes'");
        q.put("switching.adjustments.pending","SELECT count(*) FROM manual_financial_adjustment WHERE status IN ('SUBMITTED','APPROVED')");
        q.put("switching.calendar.cutoff.rejects.last15m","SELECT count(*) FROM settlement_cutoff_decision WHERE decision='REJECT' AND created_at>=now()-interval '15 minutes'");
        q.put("switching.finality.reversals.pending","SELECT count(*) FROM payment_reversal_request WHERE status IN ('REQUESTED','PARTIALLY_APPROVED','APPROVED') AND expires_at>now()");
        q.put("switching.crypto.rotation.overdue","SELECT count(*) FROM cryptographic_asset_inventory WHERE status IN ('ACTIVE','ROTATING') AND next_rotation_at<now()");
        q.put("switching.dependencies.circuit.open","SELECT count(*) FROM third_party_circuit_state WHERE state IN ('OPEN','FORCED_OPEN')");
        q.put("switching.capacity.forecast.shortfall","SELECT count(*) FROM capacity_forecast f WHERE f.created_at>=now()-interval '7 days' AND NOT EXISTS (SELECT 1 FROM governed_autoscaling_policy p WHERE p.component=f.component AND p.environment=f.environment AND p.status='ACTIVE' AND p.max_replicas>=f.required_replicas)");
        q.put("switching.evidence.unverified","SELECT count(*) FROM control_evidence_catalog WHERE status='REGISTERED' AND created_at<now()-interval '24 hours'");
        q.put("switching.rules.pending.approval","SELECT count(*) FROM decision_rule_version WHERE status IN ('READY','APPROVED')");
        q.put("switching.decommission.overdue","SELECT count(*) FROM decommission_plan WHERE status NOT IN ('COMPLETED','CANCELLED','ROLLED_BACK') AND planned_effective_at<now()");
        return Map.copyOf(q);
    }
}
