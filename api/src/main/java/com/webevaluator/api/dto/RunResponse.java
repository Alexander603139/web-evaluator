package com.webevaluator.api.dto;

import com.webevaluator.core.domain.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class RunResponse {
    private UUID jobId;
    private JobStatus status;
}