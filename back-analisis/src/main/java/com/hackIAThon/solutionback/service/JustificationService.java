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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class JustificationService {

    private final InvoiceRepository invoiceRepository;
    private final ClaimReportRepository claimReportRepository;
    private final FindingRepository findingRepository;
    private final LlmAnalysisService llmAnalysisService;

    public JustificationService(InvoiceRepository invoiceRepository,
                                ClaimReportRepository claimReportRepository,
                                FindingRepository findingRepository,
                                LlmAnalysisService llmAnalysisService) {
        this.invoiceRepository = invoiceRepository;
        this.claimReportRepository = claimReportRepository;
        this.findingRepository = findingRepository;
        this.llmAnalysisService = llmAnalysisService;
    }

    @Transactional
    public JustificationResultResponse analyzeJustification(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        ClaimReport claimReport = fetchClaimReport(invoice.getClaimId());

        List<InvoiceLine> lines = invoice.getLines();
        if (lines == null || lines.isEmpty()) {
            throw new ResourceNotFoundException("Invoice has no lines: " + invoiceId);
        }

        List<JustifiedLineResponse> justifiedLines = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        int totalUnjustified = 0;

        for (InvoiceLine line : lines) {
            LlmAnalysisService.JustificationAnalysis analysis = analyzeLine(line, claimReport.getReportText());
            InvoiceLineStatus status = InvoiceLineStatus.valueOf(analysis.status());
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
        persistJustificationFindings(invoiceId, findings);

        return new JustificationResultResponse(invoiceId, justifiedLines, totalUnjustified);
    }

    public ClaimReport fetchClaimReport(Long claimId) {
        return claimReportRepository.findByClaimId(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim report not found for claimId: " + claimId));
    }

    public LlmAnalysisService.JustificationAnalysis analyzeLine(InvoiceLine line, String claimReport) {
        return llmAnalysisService.analyzeJustification(line.getDescription(), claimReport);
    }

    public void persistJustificationFindings(Long invoiceId, List<Finding> findings) {
        if (!findings.isEmpty()) {
            findingRepository.saveAll(findings);
        }
    }
}
