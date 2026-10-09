package com.webevaluator.orchestrator.service;

import com.webevaluator.core.domain.Job;
import com.webevaluator.core.domain.JobStatus;
import com.webevaluator.core.domain.ScenarioType;
import com.webevaluator.orchestrator.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;

    @Transactional
    public Job createJob(String url, ScenarioType scenarioType, String scenarioConfig) {
        Job job = Job.builder()
                .targetUrl(url)
                .scenarioType(scenarioType)
                .status(JobStatus.PENDING)
                .requestParams(scenarioConfig != null ? scenarioConfig : "{}")
                .build();

        Job saved = jobRepository.save(job);
        log.info("Created job {} with status PENDING", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Job> getJob(UUID jobId) {
        return jobRepository.findById(jobId);
    }

    @Transactional(readOnly = true)
    public java.util.List<Job> listAll() {
        return jobRepository.findAll(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"
                )
        );
    }
}