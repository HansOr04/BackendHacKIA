package com.hackIAThon.solutionback.dto;

import java.util.List;

public record ExtractedInvoice(
    Long claimId,
    String workshopName,
    List<ExtractedInvoiceLine> lines
) {}