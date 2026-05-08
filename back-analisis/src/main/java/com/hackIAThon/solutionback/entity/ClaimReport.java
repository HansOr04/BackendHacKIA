package com.hackIAThon.solutionback.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "claim_reports", indexes = {
    @Index(name = "idx_claim_reports_claim_id", columnList = "claim_id", unique = true)
})
public class ClaimReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false, unique = true)
    private Long claimId;

    @Column(name = "report_text", nullable = false, columnDefinition = "text")
    private String reportText;

    public ClaimReport() {}

    public ClaimReport(Long claimId, String reportText) {
        this.claimId = claimId;
        this.reportText = reportText;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }

    public String getReportText() { return reportText; }
    public void setReportText(String reportText) { this.reportText = reportText; }
}
