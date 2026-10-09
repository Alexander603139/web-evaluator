package com.webevaluator.orchestrator.scheduler;

import com.webevaluator.orchestrator.worker.JobWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobScheduler {

    private final JobWorker jobWorker;

    @Scheduled(fixedDelay = 1000)
    public void pollJobs() {
        try {
            jobWorker.processNextJob();
        } catch (Exception e) {
            log.error("Error processing job", e);
        }
    }
}