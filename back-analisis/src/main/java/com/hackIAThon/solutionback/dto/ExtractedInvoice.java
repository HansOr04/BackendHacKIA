package com.hackIAThon.solutionback.dto;

import java.util.List;

public record ExtractedInvoice(
    String claimId,
    String workshopName,
    String claimReport,
    List<ExtractedInvoiceLine> lines
) {}