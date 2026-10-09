package com.webevaluator.reporting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webevaluator.core.domain.Job;
import com.webevaluator.core.domain.Report;
import com.webevaluator.reporting.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Report saveReport(Job job, Object summary, Object steps) {
        try {
            String summaryJson = objectMapper.writeValueAsString(summary);
            String stepsJson = objectMapper.writeValueAsString(steps);

            Report report = reportRepository.findById(job.getId()).orElse(new Report());
            report.setJob(job);
            report.setSummary(summaryJson);
            report.setSteps(stepsJson);
            report.setArtifacts("{}");

            return reportRepository.save(report);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize report", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Report> getReport(java.util.UUID jobId) {
        return reportRepository.findById(jobId);
    }
}