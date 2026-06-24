package com.example.switching.dashboard.dr.service;

import com.example.switching.dashboard.common.DashboardQueryGuard;
import com.example.switching.dashboard.dr.dto.DrDashboardResponse;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "switching.phase81.dashboards.enabled", havingValue = "true")
public class DrDashboardService {
    private final JdbcTemplate jdbc; private final DashboardQueryGuard guard;
    private final Instant lastBackup; private final Instant lastRestore; private final Instant lastDrill;
    private final Integer rpo; private final Integer rto; private final boolean offsite;
    public DrDashboardService(JdbcTemplate jdbc, DashboardQueryGuard guard,
      @Value("${switching.phase81.dr.last-backup-at:}") String backup,
      @Value("${switching.phase81.dr.last-restore-at:}") String restore,
      @Value("${switching.phase81.dr.last-drill-at:}") String drill,
      @Value("${switching.phase81.dr.observed-rpo-minutes:999999}") Integer rpo,
      @Value("${switching.phase81.dr.observed-rto-minutes:999999}") Integer rto,
      @Value("${switching.phase81.dr.offsite-copy-verified:false}") boolean offsite) {
      this.jdbc=jdbc; this.guard=guard; this.lastBackup=parse(backup); this.lastRestore=parse(restore);
      this.lastDrill=parse(drill); this.rpo=rpo; this.rto=rto; this.offsite=offsite;
    }
    @Transactional(readOnly=true)
    public DrDashboardResponse load(){
      guard.apply();
      var replica=jdbc.queryForObject("""
        SELECT pg_is_in_recovery() recovering,
               CASE WHEN pg_is_in_recovery() THEN EXTRACT(EPOCH FROM now()-pg_last_xact_replay_timestamp()) END lag
      """,(rs,row)->new Object[]{rs.getBoolean("recovering"),rs.getObject("lag")});
      boolean ok=lastBackup!=null&&lastRestore!=null&&lastDrill!=null&&rpo<5&&rto<30&&offsite;
      return new DrDashboardResponse(Instant.now(),(Boolean)replica[0], replica[1]==null?null:((Number)replica[1]).doubleValue(),
        lastBackup,lastRestore,lastDrill,rpo,rto,offsite,ok?"READY":"NOT_READY");
    }
    private static Instant parse(String value){return value==null||value.isBlank()?null:Instant.parse(value);}
}
