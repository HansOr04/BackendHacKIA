package com.hackIAThon.solutionback.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "tariff_price")
    private BigDecimal tariffPrice;

    @Column(name = "absolute_delta")
    private BigDecimal absoluteDelta;

    @Column(name = "percentual_delta")
    private BigDecimal percentualDelta;

    @Column(name = "tariff_references")
    private String tariffReferences;

    @Enumerated(EnumType.STRING)
    private InvoiceLineStatus status;

    public InvoiceLine() {}

    public String getClaimId() {
        return invoice != null ? invoice.getClaimId() : null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getTariffPrice() { return tariffPrice; }
    public void setTariffPrice(BigDecimal tariffPrice) { this.tariffPrice = tariffPrice; }

    public BigDecimal getAbsoluteDelta() { return absoluteDelta; }
    public void setAbsoluteDelta(BigDecimal absoluteDelta) { this.absoluteDelta = absoluteDelta; }

    public BigDecimal getPercentualDelta() { return percentualDelta; }
    public void setPercentualDelta(BigDecimal percentualDelta) { this.percentualDelta = percentualDelta; }

    public String getTariffReferences() { return tariffReferences; }
    public void setTariffReferences(String tariffReferences) { this.tariffReferences = tariffReferences; }

    public InvoiceLineStatus getStatus() { return status; }
    public void setStatus(InvoiceLineStatus status) { this.status = status; }
}