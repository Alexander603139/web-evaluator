package com.webevaluator.orchestrator.worker;

import com.webevaluator.core.domain.Job;
import com.webevaluator.core.domain.JobStatus;
import com.webevaluator.core.scenario.ScenarioContext;
import com.webevaluator.core.scenario.ScenarioEngine;
import com.webevaluator.core.scenario.ScenarioResult;
import com.webevaluator.orchestrator.repository.JobRepository;
import com.webevaluator.orchestrator.scenario.ScenarioRegistry;
import com.webevaluator.reporting.service.ReportService;
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
    private final ReportService reportService;
    private final ScenarioRegistry scenarioRegistry;

    @Transactional
    public void processNextJob() {
        Optional<Job> pendingJobOpt = jobRepository.findFirstPendingWithLock();
        if (pendingJobOpt.isEmpty()) return;

        Job job = pendingJobOpt.get();
        log.info("Processing job {} with scenario {}", job.getId(), job.getScenarioType());

        try {
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(OffsetDateTime.now());
            jobRepository.save(job);

            ScenarioEngine engine = scenarioRegistry.get(job.getScenarioType());
            ScenarioContext context = ScenarioContext.builder()
                    .jobId(job.getId())
                    .targetUrl(job.getTargetUrl())
                    .scenarioConfig(job.getRequestParams())
                    .credential(job.getCredential())
                    .build();

            ScenarioResult result = engine.execute(context);

            reportService.saveReport(job, result, result.getSteps());

            job.setStatus(result.isGoalAchieved() ? JobStatus.SUCCESS : JobStatus.FAILED);
            job.setFinishedAt(OffsetDateTime.now());
            jobRepository.save(job);
            log.info("Job {} completed with status {}", job.getId(), job.getStatus());

        } catch (Exception e) {
            log.error("Job {} failed", job.getId(), e);
            job.setStatus(JobStatus.FAILED);
            job.setError(e.getMessage());
            job.setFinishedAt(OffsetDateTime.now());
            jobRepository.save(job);
        }
    }
}