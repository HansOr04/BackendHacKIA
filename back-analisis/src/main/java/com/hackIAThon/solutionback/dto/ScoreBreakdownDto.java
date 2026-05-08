package com.hackIAThon.solutionback.dto;

public record ScoreBreakdownDto(
        int baseScore,
        int discrepanciesPenalty,
        int duplicatesPenalty,
        int unjustifiedPenalty,
        int finalScore,
        int discrepanciesCount,
        int duplicatesCount,
        int unjustifiedCount
) {}
