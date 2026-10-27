package com.webevaluator.scenario.monkey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webevaluator.core.scenario.ScenarioContext;
import com.webevaluator.core.scenario.ScenarioResult;
import com.webevaluator.uiactions.PageError;
import com.webevaluator.uiactions.PlaywrightActions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonkeyScenarioEngineTest {

    @Mock private PlaywrightActions playwright;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private MonkeyScenarioEngine engine;

    @Test
    void execute_shouldPerformActionsAndCollectSteps() {
        ScenarioContext context = ScenarioContext.builder()
                .jobId(UUID.randomUUID())
                .targetUrl("https://example.com")
                .scenarioConfig("{\"totalActions\": 3, \"maxDurationSeconds\": 60}")

                .build();

        when(playwright.findInteractiveElements())
                .thenReturn(Arrays.asList("button >> nth=0", "a[href] >> nth=1"));
        when(playwright.getElementType(anyString())).thenReturn("button");
        when(playwright.getPageErrors()).thenReturn(List.of());

        ScenarioResult result = engine.execute(context);

        assertThat(result.getTotalSteps()).isEqualTo(3);
        assertThat(result.getSteps()).hasSize(3);
        verify(playwright).navigate(anyString());
        verify(playwright, atLeast(3)).findInteractiveElements();
    }

    @Test
    void execute_shouldCapturePageErrors() {
        ScenarioContext context = ScenarioContext.builder()
                .jobId(UUID.randomUUID())
                .targetUrl("https://example.com")
                .scenarioConfig("{\"totalActions\": 1}")
                .build();

        when(playwright.findInteractiveElements())
                .thenReturn(List.of("button >> nth=0"));
        when(playwright.getElementType(anyString())).thenReturn("button");
        PageError error = new PageError("Uncaught Error: test error", null);
        when(playwright.getPageErrors()).thenReturn(List.of(error));

        ScenarioResult result = engine.execute(context);

        assertThat(result.getErrorCount()).isGreaterThan(0);
        assertThat(result.getSteps().get(0).getErrors())
                .contains("Uncaught Error: test error");
    }

    @Test
    void execute_shouldRespectTimeLimit() {
        ScenarioContext context = ScenarioContext.builder()
                .jobId(UUID.randomUUID())
                .targetUrl("https://example.com")
                .scenarioConfig("{\"totalActions\": 1000, \"maxDurationSeconds\": 1}")
                .build();

        when(playwright.findInteractiveElements())
                .thenReturn(List.of("button >> nth=0"));
        when(playwright.getElementType(anyString())).thenReturn("button");
        when(playwright.getPageErrors()).thenReturn(List.of());

        long start = System.currentTimeMillis();
        ScenarioResult result = engine.execute(context);
        long duration = System.currentTimeMillis() - start;

        assertThat(duration).isLessThan(3000);
        assertThat(result.getTotalSteps()).isLessThan(1000);
    }

    @Test
    void execute_shouldHandleException() {
        ScenarioContext context = ScenarioContext.builder()
                .jobId(UUID.randomUUID())
                .targetUrl("https://example.com")
                .scenarioConfig("{\"totalActions\": 1}")
                .build();

        doThrow(new RuntimeException("Browser crashed"))
                .when(playwright).navigate(anyString());

        ScenarioResult result = engine.execute(context);

        assertThat(result.isGoalAchieved()).isFalse();
        assertThat(result.getErrorCount()).isGreaterThan(0);
    }
}