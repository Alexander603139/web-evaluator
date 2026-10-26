package com.webevaluator.scenario.bdd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webevaluator.core.repository.ScenarioRepository;
import com.webevaluator.core.scenario.ScenarioContext;
import com.webevaluator.core.scenario.ScenarioEngine;
import com.webevaluator.core.scenario.ScenarioResult;
import lombok.RequiredArgsConstructor;
import com.webevaluator.uiactions.PlaywrightActions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class BddScenarioEngine implements ScenarioEngine {

    private final PlaywrightActions playwright;
    private final ObjectMapper objectMapper;
    private final ScenarioRepository scenarioRepository;

    @Override
    public ScenarioResult execute(ScenarioContext context) {
        BddScenarioConfig config = parseConfig(context.getScenarioConfig());
        String featureText = resolveFeatureText(config);

        List<BddScenarioStep> steps = new ArrayList<>();
        String[] lines = featureText.split("\\r?\\n");
        int stepNumber = 1;

        try {
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                BddScenarioStep step = executeStep(stepNumber++, trimmed);
                steps.add(step);
                log.info("Step {}: {} - {}", step.getStepNumber(), step.getGherkinLine(), step.getStatus());
            }

            long errorCount = steps.stream().filter(s -> "failed".equals(s.getStatus())).count();

            return ScenarioResult.builder()
                    .totalSteps(steps.size())
                    .errorCount((int) errorCount)
                    .goalAchieved(errorCount == 0)
                    .steps((List) steps)
                    .build();

        } catch (Exception e) {
            log.error("BDD scenario failed", e);
            steps.add(BddScenarioStep.builder()
                    .stepNumber(stepNumber)
                    .gherkinLine("FATAL_ERROR")
                    .status("failed")
                    .timestamp(OffsetDateTime.now())
                    .errors(List.of(e.getMessage()))
                    .build());

            return ScenarioResult.builder()
                    .totalSteps(steps.size())
                    .errorCount(1)
                    .goalAchieved(false)
                    .steps((List) steps)
                    .build();
        } finally {
            try { playwright.close(); } catch (Exception ignored) {}
        }
    }

    private BddScenarioStep executeStep(int stepNumber, String line) {
        String status = "success";
        List<String> errors = new ArrayList<>();
        String expected = null;
        String actual = null;

        try {
            // 1. открываю страницу {url}
            Matcher mOpen = Pattern.compile("^открываю страницу(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE).matcher(line);
            if (mOpen.matches()) {
                playwright.navigate(mOpen.group(1) != null ? mOpen.group(1) : "about:blank");
                return buildStep(stepNumber, line, status, errors, expected, actual);
            }

            // 2. ввожу текст {text} в поле {selector}
            Matcher mType = Pattern.compile("^ввожу текст\\s+(.+)\\s+в поле\\s+(.+)$", Pattern.CASE_INSENSITIVE).matcher(line);
            if (mType.matches()) {
                playwright.type(mType.group(2), mType.group(1));
                return buildStep(stepNumber, line, status, errors, expected, actual);
            }

            // 3. нажимаю на кнопку {selector}
            Matcher mClick = Pattern.compile("^нажимаю на кнопку\\s+(.+)$", Pattern.CASE_INSENSITIVE).matcher(line);
            if (mClick.matches()) {
                playwright.clickWithForce(mClick.group(1));
                return buildStep(stepNumber, line, status, errors, expected, actual);
            }

            // 4. заголовок страницы равен {expected}
            Matcher mTitle = Pattern.compile("^заголовок страницы равен\\s+(.+)$", Pattern.CASE_INSENSITIVE).matcher(line);
            if (mTitle.matches()) {
                expected = mTitle.group(1);
                // ВАЖНО: Убедись, что в PlaywrightActions есть метод getTitle(), возвращающий page.title()
                actual = playwright.getTitle();
                if (!expected.equals(actual)) {
                    status = "failed";
                    errors.add("Expected title: '" + expected + "', but got: '" + actual + "'");
                }
                return buildStep(stepNumber, line, status, errors, expected, actual);
            }

            // LLM-fallback заглушка
            log.warn("LLM fallback stub: unrecognized step '{}'", line);
            status = "skipped";
            errors.add("LLM fallback: step not recognized deterministically");
            return buildStep(stepNumber, line, status, errors, expected, actual);

        } catch (Exception e) {
            status = "failed";
            errors.add(e.getMessage());
            return buildStep(stepNumber, line, status, errors, expected, actual);
        }
    }

    private BddScenarioStep buildStep(int stepNumber, String line, String status, List<String> errors, String expected, String actual) {
        return BddScenarioStep.builder()
                .stepNumber(stepNumber)
                .gherkinLine(line)
                .status(status)
                .timestamp(OffsetDateTime.now())
                .errors(errors)
                .expected(expected)
                .actual(actual)
                .build();
    }

    private String resolveFeatureText(BddScenarioConfig config) {
        if (config.getFeatureText() != null && !config.getFeatureText().isBlank()) {
            return config.getFeatureText();
        }
        if (config.getScenarioId() != null) {
            return scenarioRepository.findById(config.getScenarioId())
                    .map(scenario -> scenario.getContent())
                    .orElseThrow(() -> new RuntimeException("Scenario not found with id: " + config.getScenarioId()));
        }
        throw new RuntimeException("Either featureText or scenarioId must be provided in scenarioConfig");
    }

    private BddScenarioConfig parseConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, BddScenarioConfig.class);
        } catch (Exception e) {
            log.warn("Failed to parse BDD config: {}", e.getMessage());
            return new BddScenarioConfig();
        }
    }
}