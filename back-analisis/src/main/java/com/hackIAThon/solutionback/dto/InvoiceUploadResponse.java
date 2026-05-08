package com.hackIAThon.solutionback.dto;

public record InvoiceUploadResponse(
    Long invoiceId,
    String claimId,
    String workshopName,
    int linesExtracted
) {}