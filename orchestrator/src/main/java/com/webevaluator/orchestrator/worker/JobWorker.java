package com.webevaluator.orchestrator.worker;

import com.webevaluator.core.domain.Job;
import com.webevaluator.core.domain.JobStatus;
import com.webevaluator.orchestrator.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobWorker {

    private final JobRepository jobRepository;

    @Transactional
    public void processNextJob() {
        Optional<Job> pendingJobOpt = jobRepository.findFirstPendingWithLock();

        if (pendingJobOpt.isEmpty()) {
            return;
        }

        Job job = pendingJobOpt.get();
        log.info("Processing job {} with scenario {}", job.getId(), job.getScenarioType());

        try {
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(OffsetDateTime.now());
            jobRepository.save(job);

            // Заглушечный движок - просто ждём 2 секунды
            Thread.sleep(2000);

            job.setStatus(JobStatus.SUCCESS);
            job.setFinishedAt(OffsetDateTime.now());
            jobRepository.save(job);

            log.info("Job {} completed with status SUCCESS", job.getId());

        } catch (Exception e) {
            log.error("Job {} failed", job.getId(), e);
            job.setStatus(JobStatus.FAILED);
            job.setError(e.getMessage());
            job.setFinishedAt(OffsetDateTime.now());
            jobRepository.save(job);
        }
    }
}