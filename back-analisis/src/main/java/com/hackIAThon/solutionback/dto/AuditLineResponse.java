package com.hackIAThon.solutionback.dto;

import java.math.BigDecimal;

public record AuditLineResponse(
    Long lineId,
    String description,
    String category,
    BigDecimal unitPrice,
    BigDecimal quantity,
    BigDecimal totalCharged,
    BigDecimal tariffPrice,
    BigDecimal absoluteDelta,
    BigDecimal percentualDelta,
    String status,
    String tariffReferences
) {}