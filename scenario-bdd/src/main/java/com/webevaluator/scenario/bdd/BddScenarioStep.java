package com.webevaluator.scenario.bdd;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.webevaluator.core.scenario.ScenarioStep;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("BDD")
public class BddScenarioStep extends ScenarioStep {
    private String gherkinLine;
    private String expected;
    private String actual;
}