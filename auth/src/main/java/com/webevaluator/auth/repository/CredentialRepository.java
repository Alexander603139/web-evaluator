package com.webevaluator.auth.repository;

import com.webevaluator.core.domain.Credential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {
    List<Credential> findByOwnerId(UUID ownerId);
}