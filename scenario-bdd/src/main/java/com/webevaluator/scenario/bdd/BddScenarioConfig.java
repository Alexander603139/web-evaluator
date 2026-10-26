package com.webevaluator.scenario.bdd;
import lombok.Data;
import java.util.UUID;

@Data
public class BddScenarioConfig {
    private UUID scenarioId;
    private String featureText;
}