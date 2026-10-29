package com.webevaluator.core.scenario;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@SuperBuilder
@NoArgsConstructor
// Jackson будет добавлять поле "stepType" в JSON, чтобы знать, какой класс создавать при чтении
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "stepType")
public abstract class ScenarioStep {
    private Integer stepNumber;
    private String status; // success, failed, skipped
    private OffsetDateTime timestamp;
    private List<String> errors;
    private String screenshotUrl;
}