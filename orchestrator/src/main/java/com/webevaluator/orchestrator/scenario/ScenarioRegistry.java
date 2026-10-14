package com.webevaluator.orchestrator.scenario;

import com.webevaluator.core.domain.ScenarioType;
import com.webevaluator.core.scenario.ScenarioEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScenarioRegistry {

    private final Map<String, ScenarioEngine> engines;

    public ScenarioEngine get(ScenarioType type) {
        String beanName = type.name().toLowerCase() + "ScenarioEngine";
        ScenarioEngine engine = engines.get(beanName);
        if (engine == null) {
            throw new IllegalArgumentException("No engine for scenario type: " + type);
        }
        return engine;
    }
}