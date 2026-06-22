package com.example.switching.phaseii;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;import java.time.OffsetDateTime;import java.util.*;import java.util.concurrent.*;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.jdbc.core.JdbcTemplate;
import com.example.switching.AbstractIntegrationTest;import com.example.switching.promotion.service.PromotionBudgetService;
class PromotionBudgetConcurrencyIntegrationTest extends AbstractIntegrationTest{
 @Autowired JdbcTemplate jdbc;@Autowired PromotionBudgetService budgets;UUID id;
 @BeforeEach void setup(){jdbc.update("DELETE FROM promotion_settlement");jdbc.update("DELETE FROM promotion_application");jdbc.update("DELETE FROM promotion_eligibility_rule");jdbc.update("DELETE FROM promotion");id=UUID.randomUUID();jdbc.update("""
 INSERT INTO promotion(id,code,name,promotion_type,status,priority,combinable,funder_participant_id,currency,budget_cap,budget_reserved,budget_consumed,discount_value,discount_mode,starts_at,ends_at,created_by)
 VALUES (?,?,?,'WAIVER','ACTIVE',100,false,'BANK_A','LAK',100,0,0,10,'FIXED',now()-interval '1 minute',now()+interval '1 day','test')
 """,id,"P-"+id,"Test");}
 @Test void concurrentReservationsNeverOverspend() throws Exception{try(var ex=Executors.newFixedThreadPool(20)){List<Future<Boolean>> fs=new ArrayList<>();for(int i=0;i<20;i++)fs.add(ex.submit(()->budgets.reserve(id,new BigDecimal("10"))));int accepted=0;for(var f:fs)if(f.get())accepted++;assertEquals(10,accepted);}Map<String,Object> row=jdbc.queryForMap("SELECT budget_reserved,budget_consumed,budget_cap FROM promotion WHERE id=?",id);assertEquals(0,((BigDecimal)row.get("budget_reserved")).add((BigDecimal)row.get("budget_consumed")).compareTo((BigDecimal)row.get("budget_cap")));}
}
