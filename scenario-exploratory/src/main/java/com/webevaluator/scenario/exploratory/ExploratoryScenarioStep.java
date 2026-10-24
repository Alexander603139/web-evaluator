package com.webevaluator.scenario.exploratory;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.webevaluator.core.scenario.ScenarioStep;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("EXPLORATORY")
public class ExploratoryScenarioStep extends ScenarioStep {
    private String url;
    private List<String> discoveredLinks;
}