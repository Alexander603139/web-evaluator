package com.webevaluator.reporting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webevaluator.core.domain.Job;
import com.webevaluator.core.domain.Report;
import com.webevaluator.reporting.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private ReportService reportService;

    @Test
    void saveReport_shouldSerializeAndSave() {
        Job job = Job.builder().id(UUID.randomUUID()).targetUrl("https://x.com").build();
        when(reportRepository.findById(any())).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> summary = Map.of("totalSteps", 3, "errorCount", 0, "goalAchieved", true);

        Report saved = reportService.saveReport(job, summary, java.util.List.of());

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());

        assertThat(captor.getValue().getJob()).isEqualTo(job);
        assertThat(captor.getValue().getSummary()).contains("totalSteps");
        assertThat(saved.getArtifacts()).isEqualTo("{}");
    }

    @Test
    void getReport_shouldReturnFromRepository() {
        UUID id = UUID.randomUUID();
        Report report = new Report();
        when(reportRepository.findById(id)).thenReturn(Optional.of(report));

        Optional<Report> result = reportService.getReport(id);

        assertThat(result).isPresent();
    }

    @Test
    void getReport_shouldReturnEmptyWhenNotFound() {
        when(reportRepository.findById(any())).thenReturn(Optional.empty());

        Optional<Report> result = reportService.getReport(UUID.randomUUID());

        assertThat(result).isEmpty();
    }
}