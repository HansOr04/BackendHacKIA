package com.hackIAThon.solutionback.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScoreBreakdownDto(
        @JsonProperty("baseScore") int baseScore,
        @JsonProperty("discrepanciesPenalty") int discrepanciesPenalty,
        @JsonProperty("duplicatesPenalty") int duplicatesPenalty,
        @JsonProperty("unjustifiedPenalty") int unjustifiedPenalty,
        @JsonProperty("finalScore") int finalScore,
        @JsonProperty("discrepanciesCount") int discrepanciesCount,
        @JsonProperty("duplicatesCount") int duplicatesCount,
        @JsonProperty("unjustifiedCount") int unjustifiedCount
) {}
