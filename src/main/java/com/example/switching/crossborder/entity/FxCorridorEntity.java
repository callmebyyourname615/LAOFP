package com.example.switching.crossborder.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "fx_corridors")
public class FxCorridorEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "corridor_id")        private Long corridorId;
    @Column(name = "source_currency")    private String sourceCurrency;
    @Column(name = "dest_currency")      private String destCurrency;
    @Column(name = "target_network")     private String targetNetwork;
    @Column(name = "indicative_rate",    precision = 20, scale = 8)
                                         private BigDecimal indicativeRate;
    @Column(name = "min_amount",         precision = 20, scale = 4)
                                         private BigDecimal minAmount;
    @Column(name = "max_amount",         precision = 20, scale = 4)
                                         private BigDecimal maxAmount;
    @Column(name = "fee_percent",        precision = 7,  scale = 6)
                                         private BigDecimal feePercent;
    @Column(name = "fee_fixed",          precision = 20, scale = 4)
                                         private BigDecimal feeFixed;
    @Column(name = "status")             private String status;
    @Column(name = "created_at")         private LocalDateTime createdAt;

    public Long          getCorridorId()     { return corridorId; }
    public String        getSourceCurrency() { return sourceCurrency; }
    public String        getDestCurrency()   { return destCurrency; }
    public String        getTargetNetwork()  { return targetNetwork; }
    public BigDecimal    getIndicativeRate() { return indicativeRate; }
    public BigDecimal    getMinAmount()      { return minAmount; }
    public BigDecimal    getMaxAmount()      { return maxAmount; }
    public BigDecimal    getFeePercent()     { return feePercent; }
    public BigDecimal    getFeeFixed()       { return feeFixed; }
    public String        getStatus()         { return status; }
    public LocalDateTime getCreatedAt()      { return createdAt; }
}
