package com.hackIAThon.solutionback.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackIAThon.solutionback.dto.AuditReportResponse;
import com.hackIAThon.solutionback.dto.ScoreBreakdownDto;
import com.hackIAThon.solutionback.entity.AuditResult;
import com.hackIAThon.solutionback.entity.Finding;
import com.hackIAThon.solutionback.entity.FindingType;
import com.hackIAThon.solutionback.entity.Recommendation;
import com.hackIAThon.solutionback.exception.ResourceNotFoundException;
import com.hackIAThon.solutionback.repository.AuditResultRepository;
import com.hackIAThon.solutionback.repository.FindingRepository;
import com.hackIAThon.solutionback.repository.InvoiceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AuditReportService {

    private final InvoiceRepository invoiceRepository;
    private final FindingRepository findingRepository;
    private final AuditResultRepository auditResultRepository;
    private final RiskScoreCalculator riskScoreCalculator;
    private final LlmNarrativeService llmNarrativeService;
    private final ObjectMapper objectMapper;

    @Value("${audit.approve-threshold:70}")
    private int approveThreshold;

    @Value("${audit.rules-version:1.0.0}")
    private String rulesVersion;

    public AuditReportService(InvoiceRepository invoiceRepository,
                              FindingRepository findingRepository,
                              AuditResultRepository auditResultRepository,
                              RiskScoreCalculator riskScoreCalculator,
                              LlmNarrativeService llmNarrativeService,
                              ObjectMapper objectMapper) {
        this.invoiceRepository = invoiceRepository;
        this.findingRepository = findingRepository;
        this.auditResultRepository = auditResultRepository;
        this.riskScoreCalculator = riskScoreCalculator;
        this.llmNarrativeService = llmNarrativeService;
        this.objectMapper = objectMapper;
    }

    public AuditReportResponse generateReport(Long invoiceId) {
        if (auditResultRepository.findByInvoiceId(invoiceId).isPresent()) {
            throw new IllegalStateException("Report already exists for invoiceId: " + invoiceId);
        }

        invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        ScoreBreakdownDto breakdown = riskScoreCalculator.calculate(invoiceId);
        Recommendation recommendation = breakdown.finalScore() >= approveThreshold
                ? Recommendation.APPROVE : Recommendation.ESCALATE;

        List<Finding> findings = findingRepository.findByInvoiceId(invoiceId);

        BigDecimal totalDiscrepancy = findings.stream()
                .filter(f -> f.getType() == FindingType.PRICE_EXCEEDED && f.getAbsoluteDelta() != null)
                .map(Finding::getAbsoluteDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String narrative = llmNarrativeService.generateNarrative(
                findings, breakdown.finalScore(), recommendation.name());

        String scoreBreakdownJson;
        try {
            scoreBreakdownJson = objectMapper.writeValueAsString(breakdown);
        } catch (JsonProcessingException e) {
            scoreBreakdownJson = "{}";
        }

        AuditResult result = new AuditResult(
                invoiceId,
                breakdown.finalScore(),
                recommendation,
                narrative,
                totalDiscrepancy,
                scoreBreakdownJson,
                "models/gemini-3-flash-preview",
                rulesVersion
        );

        AuditResult saved = auditResultRepository.save(result);
        return toResponse(saved);
    }

    public AuditReportResponse getReport(Long invoiceId) {
        AuditResult result = auditResultRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found for invoiceId: " + invoiceId));
        return toResponse(result);
    }

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private AuditReportResponse toResponse(AuditResult result) {
        ScoreBreakdownDto breakdown;
        try {
            breakdown = objectMapper.readValue(result.getScoreBreakdown(), ScoreBreakdownDto.class);
        } catch (Exception e) {
            breakdown = new ScoreBreakdownDto(100, 0, 0, 0, result.getRiskScore(), 0, 0, 0);
        }
        String createdAt = result.getCreatedAt() != null
                ? result.getCreatedAt().format(DT_FMT) : null;
        return new AuditReportResponse(
                result.getId(),
                result.getInvoiceId(),
                result.getRiskScore(),
                result.getRecommendation().name(),
                breakdown,
                result.getNarrativeSummary(),
                result.getTotalDiscrepancy(),
                result.getLlmModelVersion(),
                result.getRulesVersion(),
                createdAt
        );
    }
}
