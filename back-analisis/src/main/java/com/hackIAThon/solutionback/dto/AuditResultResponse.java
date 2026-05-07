package com.hackIAThon.solutionback.dto;

import java.math.BigDecimal;
import java.util.List;

public record AuditResultResponse(
    Long invoiceId,
    List<AuditLineResponse> auditedLines,
    BigDecimal totalDiscrepancy,
    int duplicatesDetected
) {}