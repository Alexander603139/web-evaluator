package com.webevaluator.api.controller;

import com.webevaluator.api.dto.RunRequest;
import com.webevaluator.api.dto.RunResponse;
import com.webevaluator.core.domain.Job;
import com.webevaluator.orchestrator.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
public class RunController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<RunResponse> createRun(@Valid @RequestBody RunRequest request) {
        log.info("Received run request: url={}, scenarioType={}", request.getUrl(), request.getScenarioType());

        Job job = jobService.createJob(
                request.getUrl(),
                request.getScenarioType(),
                request.getScenarioConfig()
        );

        RunResponse response = new RunResponse(job.getId(), job.getStatus());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<RunResponse> getRun(@PathVariable UUID jobId) {
        Optional<Job> jobOpt = jobService.getJob(jobId);

        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Job job = jobOpt.get();
        RunResponse response = new RunResponse(job.getId(), job.getStatus());
        return ResponseEntity.ok(response);
    }
}