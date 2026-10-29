package com.webevaluator.scenario.monkey;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.webevaluator.core.scenario.ScenarioStep;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("MONKEY") // Вместо @JsonSubTypes в core
public class MonkeyScenarioStep extends ScenarioStep {
    private String action;
    private String target;
    private String screenshotUrl;

}