package com.webevaluator.auth.service;

import com.webevaluator.auth.dto.ApiKeyResponse;
import com.webevaluator.core.domain.ApiKey;
import com.webevaluator.core.domain.User;
import com.webevaluator.auth.repository.ApiKeyRepository;
import com.webevaluator.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;

    @Transactional
    public ApiKeyResponse createApiKey(String username, String name) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String plainKey = "wev_" + generateRandomHex(32);
        String keyHash = hashKey(plainKey);

        ApiKey apiKey = ApiKey.builder()
                .user(user)
                .name(name)
                .keyHash(keyHash)
                .keyPrefix("wev")
                .isActive(true)
                .permissions("READ,WRITE")
                .build();

        apiKeyRepository.save(apiKey);

        return ApiKeyResponse.builder()
                .id(apiKey.getId())
                .name(apiKey.getName())
                .apiKey(plainKey) // Возвращаем только один раз!
                .permissions(apiKey.getPermissions())
                .build();
    }

    public List<ApiKeyResponse> getUserApiKeys(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return apiKeyRepository.findByUserAndIsActiveTrue(user).stream()
                .map(k -> ApiKeyResponse.builder()
                        .id(k.getId())
                        .name(k.getName())
                        .permissions(k.getPermissions())
                        .lastUsedAt(k.getLastUsedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void revokeApiKey(UUID id, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        ApiKey apiKey = apiKeyRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new RuntimeException("API Key not found"));
        apiKey.setActive(false);
        apiKeyRepository.save(apiKey);
    }

    // Улучшение 1: Возвращаем реального User для SecurityContext
    @Transactional
    public User validateAndGetUser(String plainKey) {
        String hash = hashKey(plainKey);
        return apiKeyRepository.findByKeyHashAndIsActiveTrue(hash)
                .map(apiKey -> {
                    // Улучшение 3: обновляем last_used_at
                    apiKey.setLastUsedAt(OffsetDateTime.now());
                    apiKeyRepository.save(apiKey);
                    return apiKey.getUser();
                })
                .orElse(null);
    }

    private String generateRandomHex(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length / 2];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}