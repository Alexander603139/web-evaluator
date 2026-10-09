package com.webevaluator.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webevaluator.api.dto.JobResponse;
import com.webevaluator.api.dto.RunRequest;
import com.webevaluator.api.dto.RunResponse;
import com.webevaluator.core.domain.Job;
import com.webevaluator.orchestrator.service.JobService;
import com.webevaluator.reporting.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
public class RunController {

    private final JobService jobService;
    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<RunResponse> createRun(@Valid @RequestBody RunRequest request) {
        log.info("Received run request: url={}, scenarioType={}", request.getUrl(), request.getScenarioType());
        Job job = jobService.createJob(request.getUrl(), request.getScenarioType(), request.getScenarioConfig());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new RunResponse(job.getId(), job.getStatus()));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getRun(@PathVariable("jobId") UUID jobId) {
        Optional<Job> jobOpt = jobService.getJob(jobId);
        if (jobOpt.isEmpty()) return ResponseEntity.notFound().build();

        Job job = jobOpt.get();
        JobResponse.JobResponseBuilder builder = JobResponse.builder()
                .jobId(job.getId())
                .scenarioType(job.getScenarioType())
                .status(job.getStatus())
                .targetUrl(job.getTargetUrl())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .finishedAt(job.getFinishedAt())
                .error(job.getError());

        reportService.getReport(jobId).ifPresent(report -> {
            try {
                JobResponse.Summary summary = objectMapper.readValue(
                        report.getSummary(), JobResponse.Summary.class);
                List<JobResponse.Step> steps = objectMapper.readValue(
                        report.getSteps(), new TypeReference<>() {});
                builder.report(JobResponse.ReportData.builder()
                        .summary(summary).steps(steps).build());
            } catch (Exception e) {
                log.error("Failed to parse report for job {}", jobId, e);
            }
        });

        return ResponseEntity.ok(builder.build());
    }

    @GetMapping
    public ResponseEntity<List<JobResponse>> listRuns() {
        List<JobResponse> responses = jobService.listAll().stream()
                .map(job -> JobResponse.builder()
                        .jobId(job.getId())
                        .scenarioType(job.getScenarioType())
                        .status(job.getStatus())
                        .targetUrl(job.getTargetUrl())
                        .createdAt(job.getCreatedAt())
                        .error(job.getError())
                        .build())
                .toList();
        return ResponseEntity.ok(responses);
    }
}