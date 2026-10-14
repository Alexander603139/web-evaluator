package com.webevaluator.core.scenario;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ScenarioResult {
    private int totalSteps;
    private int errorCount;
    private boolean goalAchieved;
    private List<ScenarioStep> steps;
}