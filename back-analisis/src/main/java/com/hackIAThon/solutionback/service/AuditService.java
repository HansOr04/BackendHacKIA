package com.hackIAThon.solutionback.service;

import com.hackIAThon.solutionback.dto.AuditLineResponse;
import com.hackIAThon.solutionback.dto.AuditResultResponse;
import com.hackIAThon.solutionback.entity.Finding;
import com.hackIAThon.solutionback.entity.FindingType;
import com.hackIAThon.solutionback.entity.Invoice;
import com.hackIAThon.solutionback.entity.InvoiceLine;
import com.hackIAThon.solutionback.entity.InvoiceLineStatus;
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

/**
 * Orquesta el flujo completo de auditoría de tarifario y detección de duplicados.
 *
 * El precio tarifario y la verificación de duplicados se obtienen del VectorStore
 * interno (pgvector) via RagQueryService — sin llamadas HTTP externas.
 */
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
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        
        List<InvoiceLine> lines = invoice.getLines();
        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("Invoice has no lines: " + invoiceId);
        }

        List<AuditLineResponse> auditedLines = new ArrayList<>();
        BigDecimal totalDiscrepancy = BigDecimal.ZERO;
        int duplicatesDetected = 0;
        List<Finding> findings = new ArrayList<>();

        for (InvoiceLine line : lines) {
            // 1. Consultar precio tarifario al VectorStore interno
            BigDecimal tariffPrice = ragQueryService.queryTariffPrice(line.getDescription());
            String tariffReference = ragQueryService.queryTariffReference(line.getDescription());

            line.setTariffPrice(tariffPrice);
            line.setTariffReferences(tariffReference);

            if (tariffPrice != null) {
                var deltas = calculateDeltas(line.getUnitPrice(), tariffPrice);
                line.setAbsoluteDelta(deltas.absoluteDelta());
                line.setPercentualDelta(deltas.percentualDelta());

                if (line.getUnitPrice().compareTo(tariffPrice) > 0) {
                    line.setStatus(InvoiceLineStatus.DISCREPANCY);
                    totalDiscrepancy = totalDiscrepancy.add(deltas.absoluteDelta());
                    findings.add(new Finding(invoiceId, line.getId(), FindingType.PRICE_EXCEEDED,
                        "Price exceeded tariff" + (tariffReference != null ? " — ref: " + tariffReference : "")));
                    log.info("DISCREPANCY on line {}: unit={} tariff={} delta={}",
                        line.getId(), line.getUnitPrice(), tariffPrice, deltas.absoluteDelta());
                } else {
                    line.setStatus(InvoiceLineStatus.APPROVED);
                }
            } else {
                // RAG no retornó precio → UNJUSTIFIED (fallback, no excepción)
                line.setStatus(InvoiceLineStatus.UNJUSTIFIED);
                findings.add(new Finding(invoiceId, line.getId(), FindingType.UNJUSTIFIED,
                    "No tariff price found in VectorStore for: " + line.getDescription()));
                log.warn("UNJUSTIFIED line {}: no tariff match in VectorStore for '{}'",
                    line.getId(), line.getDescription());
            }

            // 2. Verificar duplicados en el historial del VectorStore
            boolean isDuplicate = ragQueryService.checkDuplicate(
                line.getDescription(), line.getCategory(), line.getClaimId());

            if (isDuplicate) {
                line.setStatus(InvoiceLineStatus.DUPLICATE);
                duplicatesDetected++;
                findings.add(new Finding(invoiceId, line.getId(), FindingType.DUPLICATE,
                    "Duplicate item detected in claim history: " + line.getDescription()));
                log.info("DUPLICATE detected on line {}: '{}'", line.getId(), line.getDescription());
            }

            invoiceRepository.save(invoice);

            auditedLines.add(new AuditLineResponse(
                line.getId(),
                line.getDescription(),
                line.getCategory(),
                line.getUnitPrice(),
                line.getTariffPrice(),
                line.getAbsoluteDelta(),
                line.getPercentualDelta(),
                line.getStatus().name(),
                line.getTariffReferences()
            ));
        }

        persistFindings(invoiceId, findings);
        log.info("Audit completed for invoiceId={}: {} lines audited, {} findings, {} duplicates.",
            invoiceId, auditedLines.size(), findings.size(), duplicatesDetected);

        return new AuditResultResponse(invoiceId, auditedLines, totalDiscrepancy, duplicatesDetected);
    }

    public record Deltas(BigDecimal absoluteDelta, BigDecimal percentualDelta) {}

    public Deltas calculateDeltas(BigDecimal unitPrice, BigDecimal tariffPrice) {
        BigDecimal absoluteDelta = unitPrice.subtract(tariffPrice).abs();
        BigDecimal percentualDelta = absoluteDelta
            .divide(tariffPrice, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        return new Deltas(absoluteDelta, percentualDelta);
    }

    public void persistFindings(Long invoiceId, List<Finding> findings) {
        findingRepository.saveAll(findings);
    }
}