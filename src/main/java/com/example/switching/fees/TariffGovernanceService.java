package com.example.switching.fees;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class TariffGovernanceService {
    private final JdbcTemplate jdbc;
    public TariffGovernanceService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public void approve(UUID versionId,String approver,String reason){
        if(reason==null||reason.isBlank()) throw new IllegalArgumentException("approval reason is required");
        Map<String,Object> row=jdbc.queryForMap("SELECT requested_by,status FROM tariff_version WHERE id=? FOR UPDATE",versionId);
        if(approver.equals(row.get("requested_by"))) throw new IllegalArgumentException("requester cannot approve tariff");
        if(!"DRAFT".equals(row.get("status"))) throw new IllegalStateException("tariff version is not draft");
        int rules=jdbc.queryForObject("SELECT count(*) FROM tariff_rule WHERE tariff_version_id=?",Integer.class,versionId);
        if(rules<1) throw new IllegalStateException("tariff version has no rules");
        jdbc.update("UPDATE tariff_version SET status='APPROVED',approved_by=?,approval_reason=? WHERE id=?",approver,reason,versionId);
    }

    @Transactional
    public void activate(UUID versionId){
        Map<String,Object> row=jdbc.queryForMap("SELECT plan_id,status,valid_from,valid_until FROM tariff_version WHERE id=? FOR UPDATE",versionId);
        if(!"APPROVED".equals(row.get("status"))) throw new IllegalStateException("only approved tariff may be activated");
        jdbc.update("UPDATE tariff_version SET status='RETIRED' WHERE plan_id=? AND status='ACTIVE'",row.get("plan_id"));
        int changed=jdbc.update("UPDATE tariff_version SET status='ACTIVE' WHERE id=? AND valid_from<=now() AND (valid_until IS NULL OR valid_until>now())",versionId);
        if(changed!=1) throw new IllegalStateException("tariff is outside its validity window");
    }
}
