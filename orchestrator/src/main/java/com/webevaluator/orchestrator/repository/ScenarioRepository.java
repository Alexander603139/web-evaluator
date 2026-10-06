package com.webevaluator.orchestrator.repository;

import com.webevaluator.core.domain.Scenario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {
    List<Scenario> findByOwnerId(UUID ownerId);
}