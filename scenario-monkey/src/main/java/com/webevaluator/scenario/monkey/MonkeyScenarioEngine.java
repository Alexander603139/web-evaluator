package com.webevaluator.scenario.monkey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webevaluator.core.scenario.ScenarioContext;
import com.webevaluator.core.scenario.ScenarioEngine;
import com.webevaluator.core.scenario.ScenarioResult;
import com.webevaluator.core.scenario.ScenarioStep;
import com.webevaluator.uiactions.PlaywrightActions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonkeyScenarioEngine implements ScenarioEngine {

    private final PlaywrightActions playwright;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @Override
    public ScenarioResult execute(ScenarioContext context) {
        MonkeyConfig config = parseConfig(context.getScenarioConfig());
        List<ScenarioStep> steps = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        long maxDurationMillis = config.getMaxDurationSeconds() * 1000L;

        try {
            playwright.navigate(context.getTargetUrl());
            playwright.waitForLoadState("networkidle");
            log.info("Opened page: {}", context.getTargetUrl());

            for (int i = 1; i <= config.getTotalActions(); i++) {
                // Проверяем лимит времени
                if (System.currentTimeMillis() - startTime > maxDurationMillis) {
                    log.info("Reached max duration limit after {} actions", i - 1);
                    break;
                }

                MonkeyScenarioStep step = performAction(i, config);
                steps.add(step);
                log.info("Step {}: {} on {} - {}",
                        step.getStepNumber(), step.getAction(), step.getTarget(), step.getStatus());
            }

            int errorCount = steps.stream()
                    .filter(s -> !s.getErrors().isEmpty())
                    .mapToInt(s -> s.getErrors().size())
                    .sum();

            return ScenarioResult.builder()
                    .totalSteps(steps.size())
                    .errorCount(errorCount)
                    .goalAchieved(errorCount == 0)
                    .steps(steps)
                    .build();

        } catch (Exception e) {
            log.error("Monkey scenario failed", e);
            steps.add(MonkeyScenarioStep.builder()
                    .stepNumber(steps.size() + 1)
                    .action("fatal_error")
                    .target("")
                    .status("failed")
                    .timestamp(OffsetDateTime.now())
                    .errors(List.of(e.getMessage()))
                    .build());

            return ScenarioResult.builder()
                    .totalSteps(steps.size())
                    .errorCount(1)
                    .goalAchieved(false)
                    .steps(steps)
                    .build();
        } finally {
            try {
                playwright.close();
            } catch (Exception ignored) {}
        }
    }

    private MonkeyScenarioStep performAction(int stepNumber, MonkeyConfig config) {
        List<String> errors = new ArrayList<>();
        String action = "";
        String target = "";
        String status = "success";

        try {
            List<String> selectors = playwright.findInteractiveElements();
            if (selectors.isEmpty()) {
                action = "scroll";
                target = "window";
                playwright.scroll(0, random.nextInt(1000));
            } else {
                String selector = selectors.get(random.nextInt(selectors.size()));
                target = selector;

                String elementType = playwright.getElementType(selector);

                if (elementType.equals("input") || elementType.equals("textarea")) {
                    // Всегда type для полей ввода
                    action = "type";
                    String randomText = playwright.generateRandomText();
                    playwright.type(selector, randomText);
                    // Enter с вероятностью 50%
                    if (random.nextBoolean()) {
                        playwright.pressEnter();
                    }
                } else {
                    // Для кнопок/ссылок — взвешенный выбор
                    int rand = random.nextInt(100);
                    if (rand < config.getClickWeight()) {
                        action = "click";
                        playwright.clickWithForce(selector);
                    } else if (rand < config.getClickWeight() + config.getTypeWeight()) {
                        action = "type";
                        playwright.type(selector, playwright.generateRandomText());
                    } else {
                        action = "scroll";
                        playwright.scroll(0, random.nextInt(500));
                    }
                }
            }

            playwright.waitForLoadState("domcontentloaded");

        } catch (Exception e) {
            // Игнорируем ошибки клика (элемент мог исчезнуть)
            log.debug("Action skipped: {}", e.getMessage());
            if (action.isEmpty()) {
                action = "unknown";  // чтобы не было пустых действий в отчёте
            }
            status = "skipped";
        }

        // Проверяем JS-ошибки страницы
        List<String> pageErrors = playwright.getPageErrors().stream()
                .map(e -> e.getMessage())
                .toList();
        if (!pageErrors.isEmpty()) {
            errors.addAll(pageErrors);
            status = "failed";
            playwright.clearErrors();
        }

        return MonkeyScenarioStep.builder()
                .stepNumber(stepNumber)
                .action(action)
                .target(target)
                .status(status)
                .timestamp(OffsetDateTime.now())
                .errors(errors)
                .build();
    }

    private MonkeyConfig parseConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, MonkeyConfig.class);
        } catch (Exception e) {
            log.warn("Failed to parse config, using defaults: {}", e.getMessage());
            return new MonkeyConfig();
        }
    }
}