package com.webevaluator.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ApiKeyResponse {
    private UUID id;
    private String name;
    private String apiKey; // Возвращается ТОЛЬКО при создании
    private String permissions;
    private OffsetDateTime lastUsedAt;
}