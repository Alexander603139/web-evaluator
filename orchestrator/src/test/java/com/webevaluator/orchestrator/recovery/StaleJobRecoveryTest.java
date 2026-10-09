package com.webevaluator.orchestrator.recovery;

import com.webevaluator.core.domain.Job;
import com.webevaluator.core.domain.JobStatus;
import com.webevaluator.orchestrator.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaleJobRecoveryTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private StaleJobRecovery recovery;

    @Test
    void recoverStaleJobs_shouldMarkStaleRunningJobsAsFailed() {
        Job staleJob = Job.builder()
                .id(UUID.randomUUID())
                .status(JobStatus.RUNNING)
                .startedAt(OffsetDateTime.now().minusMinutes(10))
                .build();
        Job freshJob = Job.builder()
                .id(UUID.randomUUID())
                .status(JobStatus.RUNNING)
                .startedAt(OffsetDateTime.now().minusMinutes(1))
                .build();
        Job pendingJob = Job.builder()
                .id(UUID.randomUUID())
                .status(JobStatus.PENDING)
                .build();

        when(jobRepository.findAll()).thenReturn(List.of(staleJob, freshJob, pendingJob));
        when(jobRepository.save(any(Job.class))).thenAnswer(i -> i.getArgument(0));

        recovery.recoverStaleJobs();

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository, times(1)).save(captor.capture());

        Job saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(staleJob.getId());
        assertThat(saved.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(saved.getError()).contains("interrupted");
    }

    @Test
    void recoverStaleJobs_shouldDoNothingWhenNoStaleJobs() {
        Job freshJob = Job.builder()
                .id(UUID.randomUUID())
                .status(JobStatus.RUNNING)
                .startedAt(OffsetDateTime.now().minusMinutes(1))
                .build();

        when(jobRepository.findAll()).thenReturn(List.of(freshJob));

        recovery.recoverStaleJobs();

        verify(jobRepository, never()).save(any());
    }
}