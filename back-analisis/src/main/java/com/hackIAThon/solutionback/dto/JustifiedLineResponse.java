package com.hackIAThon.solutionback.dto;

public record JustifiedLineResponse(
    Long lineId,
    String description,
    String status,
    String claimExcerpt,
    String narrativeAnalysis
) {}