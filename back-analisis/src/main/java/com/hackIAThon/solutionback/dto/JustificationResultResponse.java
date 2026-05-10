package com.hackIAThon.solutionback.dto;

import java.util.List;

public record JustificationResultResponse(
    Long invoiceId,
    List<JustifiedLineResponse> justifiedLines,
    int totalUnjustified
) {}