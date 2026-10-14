package com.webevaluator.scenario.monkey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonkeyConfig {
    private int totalActions = 50;
    private int maxDurationSeconds = 60;
    private int clickWeight = 70;
    private int typeWeight = 20;
    private int scrollWeight = 10;
}