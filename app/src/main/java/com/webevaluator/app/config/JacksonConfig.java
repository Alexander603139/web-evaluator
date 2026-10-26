package com.webevaluator.app.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.webevaluator.scenario.bdd.BddScenarioStep;
import com.webevaluator.scenario.exploratory.ExploratoryScenarioStep;
import com.webevaluator.scenario.monkey.MonkeyScenarioStep;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer registerScenarioStepTypes() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.registerSubtypes(
                    MonkeyScenarioStep.class,
                    ExploratoryScenarioStep.class,
                    BddScenarioStep.class);
            builder.modulesToInstall(module);
        };
    }
}