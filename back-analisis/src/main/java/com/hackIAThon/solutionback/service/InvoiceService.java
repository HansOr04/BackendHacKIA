package com.hackIAThon.solutionback.service;

import com.hackIAThon.solutionback.dto.InvoiceUploadResponse;
import com.hackIAThon.solutionback.dto.nvidia.FacturaData;
import com.hackIAThon.solutionback.dto.nvidia.Item;
import com.hackIAThon.solutionback.entity.ClaimReport;
import com.hackIAThon.solutionback.entity.Invoice;
import com.hackIAThon.solutionback.entity.InvoiceLine;
import com.hackIAThon.solutionback.entity.InvoiceLineStatus;
import com.hackIAThon.solutionback.repository.ClaimReportRepository;
import com.hackIAThon.solutionback.repository.InvoiceLineRepository;
import com.hackIAThon.solutionback.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final AtomicLong CLAIM_COUNTER = new AtomicLong(System.currentTimeMillis() / 1000);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final ClaimReportRepository claimReportRepository;
    private final NvidiaExtractionService nvidiaExtractionService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoiceLineRepository invoiceLineRepository,
                          ClaimReportRepository claimReportRepository,
                          NvidiaExtractionService nvidiaExtractionService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.claimReportRepository = claimReportRepository;
        this.nvidiaExtractionService = nvidiaExtractionService;
    }

    public InvoiceUploadResponse uploadInvoice(MultipartFile file) {
        try {
            FacturaData factura = nvidiaExtractionService.extractFromPdf(file);

            String workshopName = factura.getComercio() != null && !factura.getComercio().isBlank()
                    ? factura.getComercio() : file.getOriginalFilename();

            // claimId: use a unique auto-incremented value since PDFs may not have one
            Long claimId = CLAIM_COUNTER.incrementAndGet();

            Invoice invoice = new Invoice(claimId, workshopName, file.getOriginalFilename());
            invoice = invoiceRepository.save(invoice);
            final Invoice savedInvoice = invoice;

            List<Item> rawItems = factura.getItems() != null ? factura.getItems() : Collections.emptyList();
            List<InvoiceLine> lines = rawItems.stream()
                    .map(item -> {
                        InvoiceLine line = new InvoiceLine();
                        line.setInvoice(savedInvoice);
                        line.setDescription(item.getDescripcion() != null ? item.getDescripcion() : "Sin descripción");
                        line.setCategory("PART");
                        line.setQuantity(item.getCantidad() != null
                                ? BigDecimal.valueOf(item.getCantidad()) : BigDecimal.ONE);
                        line.setUnitPrice(item.getPrecioUnitario() != null
                                ? BigDecimal.valueOf(item.getPrecioUnitario()) : BigDecimal.ZERO);
                        line.setStatus(InvoiceLineStatus.APPROVED);
                        return line;
                    })
                    .toList();

            invoiceLineRepository.saveAll(lines);

            // Save a default claim report so the justification step doesn't fail
            String reportText = buildDefaultReport(factura);
            claimReportRepository.findByClaimId(claimId).ifPresentOrElse(
                    existing -> {
                        existing.setReportText(reportText);
                        claimReportRepository.save(existing);
                    },
                    () -> claimReportRepository.save(new ClaimReport(claimId, reportText))
            );

            log.info("Invoice uploaded: invoiceId={}, claimId={}, workshop={}, lines={}",
                    savedInvoice.getId(), claimId, workshopName, lines.size());

            return new InvoiceUploadResponse(savedInvoice.getId(), claimId, workshopName, lines.size());

        } catch (Exception e) {
            log.error("Error uploading invoice: {}", e.getMessage(), e);
            throw new IllegalStateException("Error processing invoice PDF: " + e.getMessage(), e);
        }
    }

    private String buildDefaultReport(FacturaData factura) {
        StringBuilder sb = new StringBuilder();
        sb.append("Factura de: ").append(factura.getComercio() != null ? factura.getComercio() : "Taller desconocido");
        if (factura.getFecha() != null) {
            sb.append(". Fecha: ").append(factura.getFecha());
        }
        if (factura.getTotal() != null) {
            sb.append(". Total facturado: ").append(factura.getTotal());
        }
        if (factura.getItems() != null && !factura.getItems().isEmpty()) {
            sb.append(". Servicios: ");
            factura.getItems().forEach(i -> sb.append(i.getDescripcion()).append("; "));
        }
        return sb.toString().trim();
    }
}
