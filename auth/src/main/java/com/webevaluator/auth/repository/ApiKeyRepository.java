package com.webevaluator.auth.repository;

import com.webevaluator.core.domain.ApiKey;
import com.webevaluator.core.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHashAndIsActiveTrue(String keyHash);
    List<ApiKey> findByUserAndIsActiveTrue(User user);
    Optional<ApiKey> findByIdAndUser(UUID id, User user);
}