package com.docassistant.dto;

import lombok.Builder;

@Builder
public record AuthResponse(
    String token,
    String userId,
    String name,
    String email,
    String tier,
    int documentLimit,
    int documentsUsed,
    int queryLimit,
    int queriesUsed
) {}
