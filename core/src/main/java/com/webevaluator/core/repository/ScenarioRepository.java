package com.webevaluator.core.repository;
import com.webevaluator.core.domain.Scenario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {}