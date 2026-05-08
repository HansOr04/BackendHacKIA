package com.hackIAThon.solutionback.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_results")
public class AuditResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false, unique = true)
    private Long invoiceId;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Recommendation recommendation;

    @Column(name = "narrative_summary", nullable = false, columnDefinition = "text")
    private String narrativeSummary;

    @Column(name = "total_discrepancy", nullable = false)
    private BigDecimal totalDiscrepancy;

    @Column(name = "score_breakdown", nullable = false, columnDefinition = "text")
    private String scoreBreakdown;

    @Column(name = "llm_model_version", nullable = false)
    private String llmModelVersion;

    @Column(name = "rules_version", nullable = false)
    private String rulesVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AuditResult() {}

    public AuditResult(Long invoiceId, Integer riskScore, Recommendation recommendation,
                       String narrativeSummary, BigDecimal totalDiscrepancy, String scoreBreakdown,
                       String llmModelVersion, String rulesVersion) {
        this.invoiceId = invoiceId;
        this.riskScore = riskScore;
        this.recommendation = recommendation;
        this.narrativeSummary = narrativeSummary;
        this.totalDiscrepancy = totalDiscrepancy;
        this.scoreBreakdown = scoreBreakdown;
        this.llmModelVersion = llmModelVersion;
        this.rulesVersion = rulesVersion;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public Recommendation getRecommendation() { return recommendation; }
    public void setRecommendation(Recommendation recommendation) { this.recommendation = recommendation; }

    public String getNarrativeSummary() { return narrativeSummary; }
    public void setNarrativeSummary(String narrativeSummary) { this.narrativeSummary = narrativeSummary; }

    public BigDecimal getTotalDiscrepancy() { return totalDiscrepancy; }
    public void setTotalDiscrepancy(BigDecimal totalDiscrepancy) { this.totalDiscrepancy = totalDiscrepancy; }

    public String getScoreBreakdown() { return scoreBreakdown; }
    public void setScoreBreakdown(String scoreBreakdown) { this.scoreBreakdown = scoreBreakdown; }

    public String getLlmModelVersion() { return llmModelVersion; }
    public void setLlmModelVersion(String llmModelVersion) { this.llmModelVersion = llmModelVersion; }

    public String getRulesVersion() { return rulesVersion; }
    public void setRulesVersion(String rulesVersion) { this.rulesVersion = rulesVersion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
