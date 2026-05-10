package com.hackIAThon.solutionback.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "workshop_name", nullable = false)
    private String workshopName;

    @Column(name = "pdf_filename", nullable = false)
    private String pdfFilename;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLine> lines = new ArrayList<>();

    public Invoice() {
        this.createdAt = LocalDateTime.now();
    }

    public Invoice(Long claimId, String workshopName, String pdfFilename) {
        this();
        this.claimId = claimId;
        this.workshopName = workshopName;
        this.pdfFilename = pdfFilename;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }
    public String getWorkshopName() { return workshopName; }
    public void setWorkshopName(String workshopName) { this.workshopName = workshopName; }
    public String getPdfFilename() { return pdfFilename; }
    public void setPdfFilename(String pdfFilename) { this.pdfFilename = pdfFilename; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<InvoiceLine> getLines() { return lines; }
    public void setLines(List<InvoiceLine> lines) { this.lines = lines; }
}
