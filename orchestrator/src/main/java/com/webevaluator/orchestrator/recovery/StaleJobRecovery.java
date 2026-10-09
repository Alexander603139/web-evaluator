package com.webevaluator.orchestrator.recovery;

import com.webevaluator.core.domain.Job;
import com.webevaluator.core.domain.JobStatus;
import com.webevaluator.orchestrator.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StaleJobRecovery {

    private final JobRepository jobRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverStaleJobs() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(5);

        List<Job> staleJobs = jobRepository.findAll().stream()
                .filter(job -> job.getStatus() == JobStatus.RUNNING)
                .filter(job -> job.getStartedAt() != null && job.getStartedAt().isBefore(threshold))
                .toList();

        if (staleJobs.isEmpty()) {
            log.info("No stale jobs found");
            return;
        }

        log.warn("Found {} stale jobs, marking as FAILED", staleJobs.size());

        for (Job job : staleJobs) {
            job.setStatus(JobStatus.FAILED);
            job.setError("Job was interrupted (recovery after restart)");
            job.setFinishedAt(OffsetDateTime.now());
            jobRepository.save(job);
            log.info("Marked stale job {} as FAILED", job.getId());
        }
    }
}