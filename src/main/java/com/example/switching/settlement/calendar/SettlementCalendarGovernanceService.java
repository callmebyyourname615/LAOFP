package com.example.switching.settlement.calendar;

import com.example.switching.governance.ControlEvidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.time.*;
import java.util.*;

@Service
public class SettlementCalendarGovernanceService {
    private final JdbcTemplate jdbc;
    public SettlementCalendarGovernanceService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public void approveAndActivate(UUID calendarId,UUID changeRequestId,String approver){
        Map<String,Object> row=jdbc.queryForMap("SELECT calendar_code,version,requested_by,status,evidence_hash FROM settlement_calendar WHERE id=? FOR UPDATE",calendarId);
        Map<String,Object> change=jdbc.queryForMap("SELECT * FROM settlement_calendar_change_request WHERE id=? FOR UPDATE",changeRequestId);
        if(approver.equals(row.get("requested_by"))||approver.equals(change.get("requested_by"))) throw new IllegalArgumentException("calendar requester cannot approve");
        if(!Set.of("DRAFT","APPROVED").contains(String.valueOf(row.get("status")))||!"REQUESTED".equals(change.get("status"))) throw new IllegalStateException("calendar/change request is not approvable");
        if(!row.get("calendar_code").equals(change.get("calendar_code"))||((Number)row.get("version")).intValue()!=((Number)change.get("proposed_version")).intValue()) throw new IllegalStateException("change request does not match calendar version");
        if(!row.get("evidence_hash").equals(change.get("bundle_hash"))) throw new IllegalStateException("approved bundle hash does not match calendar evidence");
        jdbc.update("UPDATE settlement_calendar SET status='RETIRED' WHERE calendar_code=? AND status='ACTIVE'",row.get("calendar_code"));
        int changed=jdbc.update("UPDATE settlement_calendar SET status='ACTIVE',approved_by=? WHERE id=?",approver,calendarId);
        if(changed!=1) throw new IllegalStateException("calendar activation failed");
        jdbc.update("UPDATE settlement_calendar_change_request SET status='APPLIED',approved_by=?,decided_at=now() WHERE id=?",approver,changeRequestId);
    }

    @Transactional
    public CutoffDecision evaluateSubmission(String transactionReference,String calendarCode,String cycleCode,
                                             String productCode,OffsetDateTime submittedAt){
        jdbc.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(?))) AS locked",Integer.class,"cutoff|"+transactionReference);
        List<Map<String,Object>> existing=jdbc.queryForList("SELECT decision,business_date,reason,evidence_hash FROM settlement_cutoff_decision WHERE transaction_reference=?",transactionReference);
        if(!existing.isEmpty()){
            Map<String,Object> row=existing.get(0);
            return new CutoffDecision(String.valueOf(row.get("decision")),toLocalDate(row.get("business_date")),String.valueOf(row.get("reason")),String.valueOf(row.get("evidence_hash")));
        }
        Map<String,Object> calendar=jdbc.queryForMap("""
            SELECT * FROM settlement_calendar WHERE calendar_code=? AND status='ACTIVE'
              AND effective_from<=?::date AND (effective_until IS NULL OR effective_until>=?::date)
            """,calendarCode,submittedAt,submittedAt);
        UUID calendarId=(UUID)calendar.get("id");
        Map<String,Object> rule=jdbc.queryForMap("SELECT * FROM settlement_cutoff_rule WHERE calendar_id=? AND cycle_code=? AND product_code=?",
                calendarId,cycleCode,productCode);
        ZoneId zone=ZoneId.of(String.valueOf(calendar.get("timezone")));
        ZonedDateTime local=submittedAt.atZoneSameInstant(zone);
        LocalDate businessDate=nextBusinessDate(calendarId,local.toLocalDate(),weekendDays(calendar.get("weekend_days")));
        LocalTime cutoff=((java.sql.Time)rule.get("submission_cutoff")).toLocalTime().plusSeconds(((Number)rule.get("grace_seconds")).longValue());
        String decision;
        String reason;
        if(!businessDate.equals(local.toLocalDate())){
            decision=switch(String.valueOf(rule.get("late_action"))){case "NEXT_CYCLE"->"NEXT_CYCLE";case "MANUAL_REVIEW"->"MANUAL_REVIEW";default->"REJECT";};
            reason="submission date is not a settlement business day";
        }else if(local.toLocalTime().isAfter(cutoff)){
            decision=switch(String.valueOf(rule.get("late_action"))){case "NEXT_CYCLE"->"NEXT_CYCLE";case "MANUAL_REVIEW"->"MANUAL_REVIEW";default->"REJECT";};
            if("NEXT_CYCLE".equals(decision)) businessDate=nextBusinessDate(calendarId,businessDate.plusDays(1),weekendDays(calendar.get("weekend_days")));
            reason="submission cutoff exceeded";
        }else{decision="ACCEPT";reason="submission is within active cutoff";}
        String evidence=ControlEvidence.sha256(transactionReference,calendarId,rule.get("id"),submittedAt,businessDate,decision,reason);
        jdbc.update("""
            INSERT INTO settlement_cutoff_decision(id,transaction_reference,calendar_id,cutoff_rule_id,submitted_at,business_date,decision,reason,evidence_hash)
            VALUES (?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID(),transactionReference,calendarId,rule.get("id"),submittedAt,businessDate,decision,reason,evidence);
        return new CutoffDecision(decision,businessDate,reason,evidence);
    }

    public LocalDate nextBusinessDate(UUID calendarId,LocalDate candidate,Set<Integer> weekendDays){
        LocalDate current=candidate;
        for(int i=0;i<31;i++){
            boolean weekend=weekendDays.contains(current.getDayOfWeek().getValue());
            Integer holiday=jdbc.queryForObject("SELECT count(*) FROM settlement_calendar_holiday WHERE calendar_id=? AND holiday_date=? AND full_day=true",Integer.class,calendarId,current);
            if(!weekend&&(holiday==null||holiday==0)) return current;
            current=current.plusDays(1);
        }
        throw new IllegalStateException("unable to resolve business date within 31 days");
    }

    private static LocalDate toLocalDate(Object value){
        if(value instanceof LocalDate date) return date;
        if(value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(String.valueOf(value));
    }
    private static Set<Integer> weekendDays(Object value){
        try{
            if(value instanceof Array sqlArray){
                Object raw=sqlArray.getArray();
                Set<Integer> result=new HashSet<>();
                if(raw instanceof Short[] values) for(Short v:values) result.add(v.intValue());
                else if(raw instanceof Integer[] values) result.addAll(Arrays.asList(values));
                return result;
            }
        }catch(Exception e){throw new IllegalStateException("invalid weekend_days array",e);}
        return Set.of(6,7);
    }
    public record CutoffDecision(String decision,LocalDate businessDate,String reason,String evidenceHash){}
}
