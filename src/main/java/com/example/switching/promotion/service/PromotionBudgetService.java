package com.example.switching.promotion.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionBudgetService {
    private final JdbcTemplate jdbc;
    public PromotionBudgetService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional
    public boolean reserve(UUID promotionId,BigDecimal amount){
        if(amount==null||amount.signum()<0)throw new IllegalArgumentException("reservation amount must be non-negative");
        return jdbc.update("""
            UPDATE promotion SET budget_reserved=budget_reserved+?,version=version+1
             WHERE id=? AND status='ACTIVE' AND now()>=starts_at AND now()<ends_at
               AND budget_cap-budget_reserved-budget_consumed>=?
            """,amount,promotionId,amount)==1;
    }
    @Transactional
    public void consume(UUID promotionId,BigDecimal amount){
        int updated=jdbc.update("""
            UPDATE promotion SET budget_reserved=budget_reserved-?,budget_consumed=budget_consumed+?,version=version+1
             WHERE id=? AND budget_reserved>=?
            """,amount,amount,promotionId,amount);
        if(updated!=1)throw new IllegalStateException("Promotion reservation is unavailable for consumption");
    }
    @Transactional
    public void release(UUID promotionId,BigDecimal amount){
        int updated=jdbc.update("UPDATE promotion SET budget_reserved=budget_reserved-?,version=version+1 WHERE id=? AND budget_reserved>=?",amount,promotionId,amount);
        if(updated!=1)throw new IllegalStateException("Promotion reservation is unavailable for release");
    }
}
