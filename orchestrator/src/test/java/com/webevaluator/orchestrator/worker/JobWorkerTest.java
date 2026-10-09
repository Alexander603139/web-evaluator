package com.webevaluator.orchestrator.worker;

import com.webevaluator.core.domain.Job;
import com.webevaluator.core.domain.JobStatus;
import com.webevaluator.core.domain.ScenarioType;
import com.webevaluator.orchestrator.repository.JobRepository;
import com.webevaluator.reporting.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobWorkerTest {

    @Mock private JobRepository jobRepository;
    @Mock private ReportService reportService;
    @InjectMocks private JobWorker jobWorker;

    private Job makePendingJob() {
        return Job.builder()
                .id(UUID.randomUUID())
                .targetUrl("https://example.com")
                .scenarioType(ScenarioType.MONKEY)
                .status(JobStatus.PENDING)
                .requestParams("{}")
                .build();
    }

    @Test
    void processNextJob_shouldTransitionPendingToSuccess() {
        Job job = makePendingJob();
        when(jobRepository.findFirstPendingWithLock()).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        jobWorker.processNextJob();

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository, atLeast(2)).save(captor.capture());

        Job lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(lastSaved.getStatus()).isEqualTo(JobStatus.SUCCESS);
        assertThat(lastSaved.getStartedAt()).isNotNull();
        assertThat(lastSaved.getFinishedAt()).isNotNull();
        verify(reportService).saveReport(eq(job), any(Map.class), any(List.class));
    }

    @Test
    void processNextJob_shouldMarkAsFailedOnException() {
        Job job = makePendingJob();
        when(jobRepository.findFirstPendingWithLock()).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("DB error"))
                .when(jobRepository).save(argThat(j -> j.getStatus() == JobStatus.RUNNING));

        jobWorker.processNextJob();

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository, atLeast(1)).save(captor.capture());

        Job lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(lastSaved.getStatus()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void processNextJob_shouldDoNothingWhenNoPendingJobs() {
        when(jobRepository.findFirstPendingWithLock()).thenReturn(Optional.empty());

        jobWorker.processNextJob();

        verify(jobRepository, never()).save(any());
        verifyNoInteractions(reportService);
    }
}