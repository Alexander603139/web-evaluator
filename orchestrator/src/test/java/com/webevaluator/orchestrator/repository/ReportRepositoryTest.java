package com.webevaluator.orchestrator.repository;

import com.webevaluator.core.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReportRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReportRepository reportRepository;

    @Test
    void shouldPersistAndLoadReport() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        Job job = Job.builder()
                .user(user)
                .scenarioType(ScenarioType.BDD)
                .status(JobStatus.SUCCESS)
                .targetUrl("https://example.com")
                .requestParams("{}")
                .build();
        entityManager.persistAndFlush(job);

        Report report = Report.builder()
                .job(job)
                .jobId(job.getId())
                .summary("{\"passed\": 10, \"failed\": 0}")
                .steps("[{\"step\": 1, \"status\": \"passed\"}]")
                .artifacts("[{\"type\": \"screenshot\", \"url\": \"http://example.com/1.png\"}]")
                .build();
        entityManager.persistAndFlush(report);

        Report loaded = entityManager.find(Report.class, job.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getJobId()).isEqualTo(job.getId());
        assertThat(loaded.getSummary()).isEqualTo("{\"passed\": 10, \"failed\": 0}");
        assertThat(loaded.getSteps()).isEqualTo("[{\"step\": 1, \"status\": \"passed\"}]");
        assertThat(loaded.getArtifacts()).isEqualTo("[{\"type\": \"screenshot\", \"url\": \"http://example.com/1.png\"}]");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldLinkReportToJob() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        Job job = Job.builder()
                .user(user)
                .scenarioType(ScenarioType.EXPLORATORY)
                .status(JobStatus.SUCCESS)
                .targetUrl("https://example.com")
                .requestParams("{}")
                .build();
        entityManager.persistAndFlush(job);

        Report report = Report.builder()
                .job(job)
                .jobId(job.getId())
                .summary("{\"passed\": 5, \"failed\": 2}")
                .steps("[]")
                .artifacts("{}")
                .build();
        entityManager.persistAndFlush(report);

        Report loaded = entityManager.find(Report.class, job.getId());
        assertThat(loaded.getJob()).isNotNull();
        assertThat(loaded.getJob().getId()).isEqualTo(job.getId());
    }

    @Test
    void shouldPersistReportWithCreatedAt() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        Job job = Job.builder()
                .user(user)
                .scenarioType(ScenarioType.MONKEY)
                .status(JobStatus.SUCCESS)
                .targetUrl("https://example.com")
                .requestParams("{}")
                .build();
        entityManager.persistAndFlush(job);

        Report report = Report.builder()
                .job(job)
                .jobId(job.getId())
                .summary("{}")
                .steps("{}")
                .artifacts("{}")
                .build();
        entityManager.persistAndFlush(report);

        Report loaded = entityManager.find(Report.class, job.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldFindReportByJobId() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        Job job = Job.builder()
                .user(user)
                .scenarioType(ScenarioType.AI_DRIVEN)
                .status(JobStatus.SUCCESS)
                .targetUrl("https://example.com")
                .requestParams("{}")
                .build();
        entityManager.persistAndFlush(job);

        Report report = Report.builder()
                .job(job)
                .jobId(job.getId())
                .summary("{\"passed\": 1}")
                .steps("[]")
                .artifacts("{}")
                .build();
        entityManager.persistAndFlush(report);

        UUID jobId = job.getId();
        Report loaded = entityManager.find(Report.class, jobId);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getJobId()).isEqualTo(jobId);
    }
}
