package com.hackIAThon.solutionback.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuditReportResponse(
        Long reportId,
        Long invoiceId,
        Integer riskScore,
        String recommendation,
        ScoreBreakdownDto scoreBreakdown,
        String narrativeSummary,
        BigDecimal totalDiscrepancy,
        String llmModelVersion,
        String rulesVersion,
        LocalDateTime createdAt
) {}
