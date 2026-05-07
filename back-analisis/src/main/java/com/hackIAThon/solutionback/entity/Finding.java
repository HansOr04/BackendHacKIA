package com.hackIAThon.solutionback.entity;

import jakarta.persistence.*;

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

    public Finding() {}

    public Finding(Long invoiceId, Long lineId, FindingType type, String description) {
        this.invoiceId = invoiceId;
        this.lineId = lineId;
        this.type = type;
        this.description = description;
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
}