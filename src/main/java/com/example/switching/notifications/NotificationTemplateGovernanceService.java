package com.example.switching.notifications;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class NotificationTemplateGovernanceService {
    private final JdbcTemplate jdbc;
    public NotificationTemplateGovernanceService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public void approve(UUID versionId,String approver){
        Map<String,Object> row=jdbc.queryForMap("SELECT requested_by,status FROM notification_template_version WHERE id=? FOR UPDATE",versionId);
        if(approver.equals(row.get("requested_by"))) throw new IllegalArgumentException("requester cannot approve notification template");
        if(!"DRAFT".equals(row.get("status"))) throw new IllegalStateException("template version is not draft");
        jdbc.update("UPDATE notification_template_version SET status='APPROVED',approved_by=? WHERE id=?",approver,versionId);
    }

    @Transactional
    public void activate(UUID versionId){
        Map<String,Object> row=jdbc.queryForMap("SELECT template_code,channel,locale,status FROM notification_template_version WHERE id=? FOR UPDATE",versionId);
        if(!"APPROVED".equals(row.get("status"))) throw new IllegalStateException("only approved template may be activated");
        jdbc.update("UPDATE notification_template_version SET status='RETIRED' WHERE template_code=? AND channel=? AND locale=? AND status='ACTIVE'",
                row.get("template_code"),row.get("channel"),row.get("locale"));
        jdbc.update("UPDATE notification_template_version SET status='ACTIVE' WHERE id=?",versionId);
    }
}
