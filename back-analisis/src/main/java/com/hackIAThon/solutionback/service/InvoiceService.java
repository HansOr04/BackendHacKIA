package com.hackIAThon.solutionback.service;

import com.hackIAThon.solutionback.dto.ExtractedInvoice;
import com.hackIAThon.solutionback.dto.InvoiceUploadResponse;
import com.hackIAThon.solutionback.entity.Invoice;
import com.hackIAThon.solutionback.entity.InvoiceLine;
import com.hackIAThon.solutionback.entity.InvoiceLineStatus;
import com.hackIAThon.solutionback.repository.InvoiceLineRepository;
import com.hackIAThon.solutionback.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final LlmExtractionService llmExtractionService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoiceLineRepository invoiceLineRepository,
                          LlmExtractionService llmExtractionService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.llmExtractionService = llmExtractionService;
    }

    public InvoiceUploadResponse uploadInvoice(MultipartFile file) {
        try {
            InputStreamResource pdfResource = new InputStreamResource(file.getInputStream());

            // 1. Extraer datos completos del PDF vía LLM (incluyendo claimId y workshopName)
            ExtractedInvoice extracted = llmExtractionService.extractInvoice(pdfResource);

            // 2. Persistir la factura con los datos extraídos
            Invoice invoice = new Invoice(
                extracted.claimId(),
                extracted.workshopName(),
                file.getOriginalFilename()
            );
            invoice = invoiceRepository.save(invoice);
            final Invoice savedInvoice = invoice;

            // 3. Persistir líneas extraídas
            List<InvoiceLine> lines = extracted.lines().stream()
                .map(line -> {
                    InvoiceLine invoiceLine = new InvoiceLine();
                    invoiceLine.setInvoice(savedInvoice);
                    invoiceLine.setDescription(line.description());
                    invoiceLine.setCategory(line.category());
                    invoiceLine.setQuantity(line.quantity() != null ? line.quantity() : java.math.BigDecimal.ONE);
                    invoiceLine.setUnitPrice(line.unitPrice());
                    invoiceLine.setStatus(InvoiceLineStatus.APPROVED);
                    return invoiceLine;
                })
                .toList();

            invoiceLineRepository.saveAll(lines);



            log.info("Invoice uploaded: invoiceId={}, claimId={}, lines={}",
                savedInvoice.getId(), extracted.claimId(), lines.size());

            return new InvoiceUploadResponse(
                savedInvoice.getId(),
                extracted.claimId(),
                extracted.workshopName(),
                lines.size()
            );

        } catch (Exception e) {
            log.error("Error uploading invoice: {}", e.getMessage());
            throw new RuntimeException("Error processing invoice PDF: " + e.getMessage());
        }
    }
}