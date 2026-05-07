package com.hackIAThon.solutionback.dto;

import java.math.BigDecimal;

public record ExtractedInvoiceLine(
    String description,
    String category,
    BigDecimal unitPrice
) {}