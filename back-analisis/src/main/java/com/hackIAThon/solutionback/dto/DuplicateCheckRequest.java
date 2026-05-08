package com.hackIAThon.solutionback.dto;

public record DuplicateCheckRequest(
    String description,
    String claimId
) {}