package com.webevaluator.orchestrator.worker;

import com.webevaluator.core.domain.Job;
import com.webevaluator.core.domain.JobStatus;
import com.webevaluator.orchestrator.repository.JobRepository;
import com.webevaluator.reporting.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobWorker {

    private final JobRepository jobRepository;
    private final ReportService reportService;

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

            Thread.sleep(2000);

            // Заглушечный отчёт
            Map<String, Object> summary = Map.of(
                    "totalSteps", 1,
                    "errorCount", 0,
                    "goalAchieved", true
            );
            List<Map<String, Object>> steps = List.of(Map.of(
                    "stepNumber", 1,
                    "action", "stub_action",
                    "status", "success",
                    "timestamp", OffsetDateTime.now().toString(),
                    "errors", List.of()
            ));
            reportService.saveReport(job, summary, steps);

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