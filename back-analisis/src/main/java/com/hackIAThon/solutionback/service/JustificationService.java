package com.hackIAThon.solutionback.service;

import com.hackIAThon.solutionback.dto.JustifiedLineResponse;
import com.hackIAThon.solutionback.dto.JustificationResultResponse;
import com.hackIAThon.solutionback.entity.ClaimReport;
import com.hackIAThon.solutionback.entity.Finding;
import com.hackIAThon.solutionback.entity.FindingType;
import com.hackIAThon.solutionback.entity.Invoice;
import com.hackIAThon.solutionback.entity.InvoiceLine;
import com.hackIAThon.solutionback.entity.InvoiceLineStatus;
import com.hackIAThon.solutionback.exception.ResourceNotFoundException;
import com.hackIAThon.solutionback.repository.ClaimReportRepository;
import com.hackIAThon.solutionback.repository.FindingRepository;
import com.hackIAThon.solutionback.repository.InvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class JustificationService {

    private final InvoiceRepository invoiceRepository;
    private final ClaimReportRepository claimReportRepository;
    private final FindingRepository findingRepository;
    private final LlmAnalysisService llmAnalysisService;

    @Autowired @Lazy
    private JustificationService self;

    public JustificationService(InvoiceRepository invoiceRepository,
                                ClaimReportRepository claimReportRepository,
                                FindingRepository findingRepository,
                                LlmAnalysisService llmAnalysisService) {
        this.invoiceRepository = invoiceRepository;
        this.claimReportRepository = claimReportRepository;
        this.findingRepository = findingRepository;
        this.llmAnalysisService = llmAnalysisService;
    }

    public JustificationResultResponse analyzeJustification(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        ClaimReport claimReport = fetchClaimReport(invoice.getClaimId());

        List<InvoiceLine> lines = invoice.getLines();
        if (lines == null || lines.isEmpty()) {
            return new JustificationResultResponse(invoiceId, List.of(), 0);
        }

        List<JustifiedLineResponse> justifiedLines = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        int totalUnjustified = 0;

        for (InvoiceLine line : lines) {
            LlmAnalysisService.JustificationAnalysis analysis = self.analyzeLine(line, claimReport.getReportText());
            InvoiceLineStatus status;
            try {
                status = InvoiceLineStatus.valueOf(analysis.status());
            } catch (IllegalArgumentException ex) {
                status = InvoiceLineStatus.UNJUSTIFIED;
            }
            line.setStatus(status);

            if (InvoiceLineStatus.UNJUSTIFIED.equals(status)) {
                totalUnjustified++;
                findings.add(new Finding(
                        invoiceId,
                        line.getId(),
                        FindingType.UNJUSTIFIED,
                        line.getDescription(),
                        analysis.claimExcerpt(),
                        analysis.narrativeAnalysis()
                ));
            }

            justifiedLines.add(new JustifiedLineResponse(
                    line.getId(),
                    line.getDescription(),
                    status.name(),
                    analysis.claimExcerpt(),
                    analysis.narrativeAnalysis()
            ));
        }

        invoiceRepository.save(invoice);
        self.persistJustificationFindings(findings);

        return new JustificationResultResponse(invoiceId, justifiedLines, totalUnjustified);
    }

    public ClaimReport fetchClaimReport(Long claimId) {
        return claimReportRepository.findByClaimId(claimId)
                .orElseGet(() -> new ClaimReport(claimId, "Sin reporte disponible."));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LlmAnalysisService.JustificationAnalysis analyzeLine(InvoiceLine line, String claimReport) {
        return llmAnalysisService.analyzeJustification(line.getDescription(), claimReport);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistJustificationFindings(List<Finding> findings) {
        if (!findings.isEmpty()) {
            findingRepository.saveAll(findings);
        }
    }
}
