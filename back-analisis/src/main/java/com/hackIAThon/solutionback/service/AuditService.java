package com.hackIAThon.solutionback.service;

import com.hackIAThon.solutionback.dto.AuditLineResponse;
import com.hackIAThon.solutionback.dto.AuditResultResponse;
import com.hackIAThon.solutionback.entity.Finding;
import com.hackIAThon.solutionback.entity.FindingType;
import com.hackIAThon.solutionback.entity.Invoice;
import com.hackIAThon.solutionback.entity.InvoiceLine;
import com.hackIAThon.solutionback.entity.InvoiceLineStatus;
import com.hackIAThon.solutionback.exception.ResourceNotFoundException;
import com.hackIAThon.solutionback.repository.FindingRepository;
import com.hackIAThon.solutionback.repository.InvoiceRepository;
import com.hackIAThon.solutionback.service.rag.RagQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final InvoiceRepository invoiceRepository;
    private final FindingRepository findingRepository;
    private final RagQueryService ragQueryService;

    public AuditService(InvoiceRepository invoiceRepository,
                        FindingRepository findingRepository,
                        RagQueryService ragQueryService) {
        this.invoiceRepository = invoiceRepository;
        this.findingRepository = findingRepository;
        this.ragQueryService = ragQueryService;
    }

    public AuditResultResponse auditInvoice(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        List<InvoiceLine> lines = invoice.getLines();
        if (lines == null || lines.isEmpty()) {
            throw new ResourceNotFoundException("Invoice has no lines: " + invoiceId);
        }

        List<AuditLineResponse> auditedLines = new ArrayList<>();
        BigDecimal totalDiscrepancy = BigDecimal.ZERO;
        int duplicatesDetected = 0;
        List<Finding> findings = new ArrayList<>();

        for (InvoiceLine line : lines) {
            BigDecimal quantity = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ONE;
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Invalid quantity on line " + line.getId());
            }
            if (line.getUnitPrice() == null || line.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Invalid unit price on line " + line.getId());
            }

            BigDecimal tariffPrice = ragQueryService.queryTariffPrice(line.getDescription());
            String tariffReference = ragQueryService.queryTariffReference(line.getDescription());

            line.setTariffPrice(tariffPrice);
            line.setTariffReferences(tariffReference);

            if (tariffPrice != null) {
                BigDecimal totalCharged = line.getUnitPrice().multiply(quantity);
                BigDecimal tariffTotal = tariffPrice.multiply(quantity);

                var deltas = calculateDeltas(totalCharged, tariffTotal);
                line.setAbsoluteDelta(deltas.absoluteDelta());
                line.setPercentualDelta(deltas.percentualDelta());

                if (totalCharged.compareTo(tariffTotal) > 0) {
                    line.setStatus(InvoiceLineStatus.DISCREPANCY);
                    totalDiscrepancy = totalDiscrepancy.add(deltas.absoluteDelta());
                    Finding priceFinding = new Finding(invoiceId, line.getId(), FindingType.PRICE_EXCEEDED,
                        "Price exceeded tariff" + (tariffReference != null ? " — ref: " + tariffReference : ""),
                        null, null);
                    priceFinding.setAbsoluteDelta(deltas.absoluteDelta());
                    priceFinding.setDeltaPercentual(deltas.percentualDelta());
                    findings.add(priceFinding);
                    log.info("DISCREPANCY on line {}: charged={} tariffTotal={} delta={}",
                        line.getId(), totalCharged, tariffTotal, deltas.absoluteDelta());
                } else if (totalCharged.compareTo(tariffTotal) < 0) {
                    line.setStatus(InvoiceLineStatus.UNDERCHARGED);
                    findings.add(new Finding(invoiceId, line.getId(), FindingType.PRICE_UNDER_TARIFF,
                        "Undercharged compared to tariff" + (tariffReference != null ? " — ref: " + tariffReference : ""),
                        null, null));
                    log.info("UNDERCHARGED on line {}: charged={} tariffTotal={} delta={}",
                        line.getId(), totalCharged, tariffTotal, deltas.absoluteDelta());
                } else {
                    line.setStatus(InvoiceLineStatus.APPROVED);
                }
            } else {
                line.setStatus(InvoiceLineStatus.UNJUSTIFIED);
                findings.add(new Finding(invoiceId, line.getId(), FindingType.UNJUSTIFIED,
                    "No tariff price found in VectorStore for: " + line.getDescription(),
                    null, null));
                log.warn("UNJUSTIFIED line {}: no tariff match in VectorStore for '{}'",
                    line.getId(), line.getDescription());
            }

            boolean isDuplicate = ragQueryService.checkDuplicate(
                    line.getDescription(), line.getCategory(), line.getInvoice().getClaimId());

            if (isDuplicate) {
                line.setStatus(InvoiceLineStatus.DUPLICATE);
                duplicatesDetected++;
                findings.add(new Finding(invoiceId, line.getId(), FindingType.DUPLICATE,
                    "Duplicate item detected in claim history: " + line.getDescription(),
                    null, null));
                log.info("DUPLICATE detected on line {}: '{}'", line.getId(), line.getDescription());
            }

            BigDecimal totalCharged = line.getUnitPrice().multiply(quantity);

            auditedLines.add(new AuditLineResponse(
                line.getId(),
                line.getDescription(),
                line.getCategory(),
                line.getUnitPrice(),
                quantity,
                totalCharged,
                line.getTariffPrice(),
                line.getAbsoluteDelta(),
                line.getPercentualDelta(),
                line.getStatus().name(),
                line.getTariffReferences()
            ));
        }

        invoiceRepository.save(invoice);
        persistFindings(invoiceId, findings);
        log.info("Audit completed for invoiceId={}: {} lines audited, {} findings, {} duplicates.",
            invoiceId, auditedLines.size(), findings.size(), duplicatesDetected);

        return new AuditResultResponse(invoiceId, auditedLines, totalDiscrepancy, duplicatesDetected);
    }

    public record Deltas(BigDecimal absoluteDelta, BigDecimal percentualDelta) {}

    public Deltas calculateDeltas(BigDecimal totalCharged, BigDecimal tariffTotal) {
        BigDecimal absoluteDelta = totalCharged.subtract(tariffTotal).abs();
        if (tariffTotal.compareTo(BigDecimal.ZERO) == 0) {
            return new Deltas(absoluteDelta, BigDecimal.ZERO);
        }
        BigDecimal percentualDelta = absoluteDelta
            .divide(tariffTotal, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        return new Deltas(absoluteDelta, percentualDelta);
    }

    public void persistFindings(Long invoiceId, List<Finding> findings) {
        findingRepository.saveAll(findings);
    }
}