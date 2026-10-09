package com.webevaluator.api.dto;

import com.webevaluator.core.domain.JobStatus;
import com.webevaluator.core.domain.ScenarioType;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class JobResponse {
    private UUID jobId;
    private ScenarioType scenarioType;
    private JobStatus status;
    private String targetUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private String error;
    private ReportData report;

    @Data
    @Builder
    public static class ReportData {
        private Summary summary;
        private List<Step> steps;
    }

    @Data
    @Builder
    public static class Summary {
        private int totalSteps;
        private int errorCount;
        private boolean goalAchieved;
    }

    @Data
    @Builder
    public static class Step {
        private int stepNumber;
        private String action;
        private String status;
        private OffsetDateTime timestamp;
        private List<String> errors;
    }
}