package com.example.switching.promotion.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.example.switching.promotion.enums.PromotionStatus;
import com.example.switching.promotion.enums.PromotionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name="promotion") @Getter @Setter @NoArgsConstructor
public class PromotionEntity {
    @Id private UUID id;
    @Column(nullable=false,unique=true,length=64) private String code;
    @Column(nullable=false,length=160) private String name;
    @Enumerated(EnumType.STRING) @Column(name="promotion_type",nullable=false,length=24) private PromotionType promotionType;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private PromotionStatus status;
    @Column(nullable=false) private int priority;
    @Column(nullable=false) private boolean combinable;
    @Column(name="funder_participant_id",nullable=false,length=64) private String funderParticipantId;
    @Column(nullable=false,length=3) private String currency;
    @Column(name="budget_cap",nullable=false,precision=19,scale=4) private BigDecimal budgetCap;
    @Column(name="budget_reserved",nullable=false,precision=19,scale=4) private BigDecimal budgetReserved;
    @Column(name="budget_consumed",nullable=false,precision=19,scale=4) private BigDecimal budgetConsumed;
    @Column(name="discount_value",nullable=false,precision=19,scale=4) private BigDecimal discountValue;
    @Column(name="discount_mode",nullable=false,length=16) private String discountMode;
    @Column(name="starts_at",nullable=false) private Instant startsAt;
    @Column(name="ends_at",nullable=false) private Instant endsAt;
    @Column(name="created_by",nullable=false,length=128) private String createdBy;
    @Column(name="suspended_by",length=128) private String suspendedBy;
    @Column(name="suspended_at") private Instant suspendedAt;
    @Column(name="created_at",insertable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",insertable=false,updatable=false) private Instant updatedAt;
    @Version private long version;
}
