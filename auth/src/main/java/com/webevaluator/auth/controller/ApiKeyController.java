package com.webevaluator.auth.controller;

import com.webevaluator.auth.dto.ApiKeyResponse;
import com.webevaluator.auth.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    public ResponseEntity<ApiKeyResponse> createKey(@RequestParam String name, Authentication authentication) {
        return ResponseEntity.ok(apiKeyService.createApiKey(authentication.getName(), name));
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> getKeys(Authentication authentication) {
        return ResponseEntity.ok(apiKeyService.getUserApiKeys(authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeKey(@PathVariable UUID id, Authentication authentication) {
        apiKeyService.revokeApiKey(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}