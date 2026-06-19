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
@Table(name = "fx_quotes")
public class FxQuoteEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quote_id")           private Long quoteId;
    @Column(name = "corridor_id")        private Long corridorId;
    @Column(name = "source_currency")    private String sourceCurrency;
    @Column(name = "dest_currency")      private String destCurrency;
    @Column(name = "source_amount",      precision = 20, scale = 4)
                                         private BigDecimal sourceAmount;
    @Column(name = "dest_amount",        precision = 20, scale = 4)
                                         private BigDecimal destAmount;
    @Column(name = "rate",               precision = 20, scale = 8)
                                         private BigDecimal rate;
    @Column(name = "fee",                precision = 20, scale = 4)
                                         private BigDecimal fee;
    @Column(name = "issued_at")          private LocalDateTime issuedAt;
    @Column(name = "expires_at")         private LocalDateTime expiresAt;
    @Column(name = "used")               private boolean used;

    public Long          getQuoteId()       { return quoteId; }
    public Long          getCorridorId()    { return corridorId; }
    public String        getSourceCurrency(){ return sourceCurrency; }
    public String        getDestCurrency()  { return destCurrency; }
    public BigDecimal    getSourceAmount()  { return sourceAmount; }
    public BigDecimal    getDestAmount()    { return destAmount; }
    public BigDecimal    getRate()          { return rate; }
    public BigDecimal    getFee()           { return fee; }
    public LocalDateTime getIssuedAt()      { return issuedAt; }
    public LocalDateTime getExpiresAt()     { return expiresAt; }
    public boolean       isUsed()           { return used; }

    public void setCorridorId(Long v)        { this.corridorId = v; }
    public void setSourceCurrency(String v)  { this.sourceCurrency = v; }
    public void setDestCurrency(String v)    { this.destCurrency = v; }
    public void setSourceAmount(BigDecimal v){ this.sourceAmount = v; }
    public void setDestAmount(BigDecimal v)  { this.destAmount = v; }
    public void setRate(BigDecimal v)        { this.rate = v; }
    public void setFee(BigDecimal v)         { this.fee = v; }
    public void setIssuedAt(LocalDateTime v) { this.issuedAt = v; }
    public void setExpiresAt(LocalDateTime v){ this.expiresAt = v; }
    public void setUsed(boolean v)           { this.used = v; }
}
