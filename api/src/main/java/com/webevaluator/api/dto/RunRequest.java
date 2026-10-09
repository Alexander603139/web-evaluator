package com.webevaluator.api.dto;

import com.webevaluator.core.domain.ScenarioType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RunRequest {
    @NotBlank(message = "URL is required")
    private String url;

    @NotNull(message = "scenarioType is required")
    private ScenarioType scenarioType;

    private String scenarioConfig;
}