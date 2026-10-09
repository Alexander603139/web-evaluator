package com.webevaluator.orchestrator.service;

import com.webevaluator.core.domain.Job;
import com.webevaluator.core.domain.JobStatus;
import com.webevaluator.core.domain.ScenarioType;
import com.webevaluator.orchestrator.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobService jobService;

    @Test
    void createJob_shouldSaveJobWithPendingStatus() {
        when(jobRepository.save(any(Job.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Job result = jobService.createJob("https://example.com", ScenarioType.MONKEY, "{}");

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(captor.capture());
        Job saved = captor.getValue();

        assertThat(saved.getTargetUrl()).isEqualTo("https://example.com");
        assertThat(saved.getScenarioType()).isEqualTo(ScenarioType.MONKEY);
        assertThat(saved.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(saved.getRequestParams()).isEqualTo("{}");
    }

    @Test
    void createJob_shouldUseEmptyJsonWhenConfigIsNull() {
        when(jobRepository.save(any(Job.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        jobService.createJob("https://example.com", ScenarioType.BDD, null);

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(captor.capture());
        assertThat(captor.getValue().getRequestParams()).isEqualTo("{}");
    }

    @Test
    void getJob_shouldReturnFromRepository() {
        UUID id = UUID.randomUUID();
        Job job = Job.builder().targetUrl("https://test.com").build();
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        Optional<Job> result = jobService.getJob(id);

        assertThat(result).isPresent();
        assertThat(result.get().getTargetUrl()).isEqualTo("https://test.com");
    }

    @Test
    void getJob_shouldReturnEmptyWhenNotFound() {
        when(jobRepository.findById(any())).thenReturn(Optional.empty());

        Optional<Job> result = jobService.getJob(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void listAll_shouldReturnAllJobsFromRepository() {
        Job job1 = Job.builder().targetUrl("https://a.com").build();
        Job job2 = Job.builder().targetUrl("https://b.com").build();
        when(jobRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(java.util.List.of(job1, job2));

        java.util.List<Job> result = jobService.listAll();

        assertThat(result).hasSize(2);
    }
}