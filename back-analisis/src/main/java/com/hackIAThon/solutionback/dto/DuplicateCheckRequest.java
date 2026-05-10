package com.hackIAThon.solutionback.dto;

public record DuplicateCheckRequest(
    String description,
    Long claimId
) {}