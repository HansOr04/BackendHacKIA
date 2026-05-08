package com.hackIAThon.solutionback.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "findings")
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingType type;

    @Column
    private String description;

    @Column(name = "claim_excerpt", columnDefinition = "text")
    private String claimExcerpt;

    @Column(name = "narrative_analysis", columnDefinition = "text")
    private String narrativeAnalysis;

    @Column(name = "absolute_delta")
    private BigDecimal absoluteDelta;

    @Column(name = "delta_percentual")
    private BigDecimal deltaPercentual;

    public Finding() {}

    public Finding(Long invoiceId, Long lineId, FindingType type, String description,
                   String claimExcerpt, String narrativeAnalysis) {
        this.invoiceId = invoiceId;
        this.lineId = lineId;
        this.type = type;
        this.description = description;
        this.claimExcerpt = claimExcerpt;
        this.narrativeAnalysis = narrativeAnalysis;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }

    public Long getLineId() { return lineId; }
    public void setLineId(Long lineId) { this.lineId = lineId; }

    public FindingType getType() { return type; }
    public void setType(FindingType type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getClaimExcerpt() { return claimExcerpt; }
    public void setClaimExcerpt(String claimExcerpt) { this.claimExcerpt = claimExcerpt; }

    public String getNarrativeAnalysis() { return narrativeAnalysis; }
    public void setNarrativeAnalysis(String narrativeAnalysis) { this.narrativeAnalysis = narrativeAnalysis; }

    public BigDecimal getAbsoluteDelta() { return absoluteDelta; }
    public void setAbsoluteDelta(BigDecimal absoluteDelta) { this.absoluteDelta = absoluteDelta; }

    public BigDecimal getDeltaPercentual() { return deltaPercentual; }
    public void setDeltaPercentual(BigDecimal deltaPercentual) { this.deltaPercentual = deltaPercentual; }
}
