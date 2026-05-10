package com.hackIAThon.solutionback.dto;

public record AuthResponse(
    String token,
    String email,
    String name
) {}
