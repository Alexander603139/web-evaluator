package com.webevaluator.orchestrator.repository;

import com.webevaluator.core.domain.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
    List<Job> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query(value = "SELECT * FROM jobs WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<Job> findFirstPendingWithLock();
}