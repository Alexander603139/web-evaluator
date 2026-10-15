package com.webevaluator.core.scenario;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class ScenarioStep {
    private int stepNumber;
    private String action;       // click, type, scroll
    private String target;       // селектор элемента
    private String status;       // success, failed, skipped
    private OffsetDateTime timestamp;
    private List<String> errors;
}