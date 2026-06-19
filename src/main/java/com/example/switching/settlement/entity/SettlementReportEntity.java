package com.example.switching.settlement.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "settlement_reports")
public class SettlementReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "psp_id", nullable = false, length = 32)
    private String pspId;

    @Column(name = "report_type", nullable = false, length = 20)
    private String reportType = "CAMT054";

    @Column(name = "report_ref", nullable = false, unique = true, length = 80)
    private String reportRef;

    @Column(name = "content")
    private String content;

    @Column(name = "generated_at", insertable = false, updatable = false)
    private LocalDateTime generatedAt;

    public Long getId()                            { return id; }
    public Long getCycleId()                       { return cycleId; }
    public void setCycleId(Long v)                 { this.cycleId = v; }
    public String getPspId()                       { return pspId; }
    public void setPspId(String v)                 { this.pspId = v; }
    public String getReportType()                  { return reportType; }
    public void setReportType(String v)            { this.reportType = v; }
    public String getReportRef()                   { return reportRef; }
    public void setReportRef(String v)             { this.reportRef = v; }
    public String getContent()                     { return content; }
    public void setContent(String v)               { this.content = v; }
    public LocalDateTime getGeneratedAt()          { return generatedAt; }
}
