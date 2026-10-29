package com.webevaluator.orchestrator.worker;

import com.webevaluator.core.domain.Job;
import com.webevaluator.core.domain.JobStatus;
import com.webevaluator.core.scenario.ScenarioContext;
import com.webevaluator.core.scenario.ScenarioEngine;
import com.webevaluator.core.scenario.ScenarioResult;
import com.webevaluator.core.storage.ArtifactUploader;
import com.webevaluator.orchestrator.repository.JobRepository;
import com.webevaluator.orchestrator.scenario.ScenarioRegistry;
import com.webevaluator.reporting.service.ArtifactStorageService;
import com.webevaluator.reporting.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobWorker {

    private final JobRepository jobRepository;
    private final ReportService reportService;
    private final ScenarioRegistry scenarioRegistry;
    private final ArtifactUploader artifactUploader;
    private final ArtifactStorageService artifactStorageService;
    private final ObjectMapper objectMapper;

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
                    .artifactUploader(artifactUploader)
                    .build();

            ScenarioResult result = engine.execute(context);
            Map<String, Object> artifacts = new HashMap<>();
            List<String> screenshots = new ArrayList<>();
            for (var step : result.getSteps()) {
                if (step.getScreenshotUrl() != null) screenshots.add(step.getScreenshotUrl());
            }
            artifacts.put("screenshots", screenshots);
            String summaryJson = objectMapper.writeValueAsString(result); // или используй свой маппинг
            String stepsJson = objectMapper.writeValueAsString(result.getSteps());
            String htmlUrl = artifactStorageService.generateHtmlReport(job, summaryJson, stepsJson);
            artifacts.put("htmlReport", htmlUrl);

            reportService.saveReport(job, result, result.getSteps(), artifacts);

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