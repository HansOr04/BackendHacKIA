package com.hackIAThon.solutionback.dto;

public record InvoiceUploadResponse(
    Long invoiceId,
    Long claimId,
    String workshopName,
    int linesExtracted
) {}