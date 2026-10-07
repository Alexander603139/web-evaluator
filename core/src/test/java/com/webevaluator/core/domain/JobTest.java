package com.webevaluator.core.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JobTest {
    @Test
    void shouldSetDefaultStatusToPending() {
        Job job = Job.builder()
                .targetUrl("https://example.com")
                .requestParams("{}")
                .build();

        job.onCreate();

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getCreatedAt()).isNotNull();
    }
}