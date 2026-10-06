package com.webevaluator.orchestrator.repository;

import com.webevaluator.core.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class JobRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JobRepository jobRepository;

    @Test
    void shouldFindJobsByUserIdOrderedByCreatedAtDesc() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        Job job1 = Job.builder()
                .user(user)
                .scenarioType(ScenarioType.BDD)
                .status(JobStatus.SUCCESS)
                .targetUrl("https://example.com")
                .requestParams("{}")
                .build();
        entityManager.persistAndFlush(job1);

        Job job2 = Job.builder()
                .user(user)
                .scenarioType(ScenarioType.EXPLORATORY)
                .status(JobStatus.FAILED)
                .targetUrl("https://test.com")
                .requestParams("{}")
                .build();
        entityManager.persistAndFlush(job2);

        User otherUser = User.builder()
                .username("otheruser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(otherUser);

        Job otherJob = Job.builder()
                .user(otherUser)
                .scenarioType(ScenarioType.MONKEY)
                .status(JobStatus.RUNNING)
                .targetUrl("https://other.com")
                .requestParams("{}")
                .build();
        entityManager.persistAndFlush(otherJob);

        List<Job> jobs = jobRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0).getId()).isEqualTo(job2.getId());
        assertThat(jobs.get(1).getId()).isEqualTo(job1.getId());
    }

    @Test
    void shouldReturnEmptyListWhenNoJobsForUser() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        List<Job> jobs = jobRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        assertThat(jobs).isEmpty();
    }

    @Test
    void shouldSetDefaultStatusToPending() {
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
                .targetUrl("https://example.com")
                .requestParams("{}")
                .build();
        entityManager.persistAndFlush(job);

        Job loaded = entityManager.find(Job.class, job.getId());
        assertThat(loaded.getStatus()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    void shouldPersistJobWithCreatedAt() {
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
                .status(JobStatus.PENDING)
                .targetUrl("https://example.com")
                .requestParams("{\"key\":\"value\"}")
                .build();
        entityManager.persistAndFlush(job);

        Job loaded = entityManager.find(Job.class, job.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldPersistJobWithAllOptionalFields() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        Scenario scenario = Scenario.builder()
                .owner(user)
                .name("test-scenario")
                .content("{}")
                .build();
        entityManager.persistAndFlush(scenario);

        Job job = Job.builder()
                .user(user)
                .scenarioType(ScenarioType.BDD)
                .status(JobStatus.RUNNING)
                .targetUrl("https://example.com")
                .requestParams("{}")
                .scenario(scenario)
                .callbackUrl("https://callback.com/webhook")
                .error("some error")
                .lockedBy("worker-1")
                .build();
        entityManager.persistAndFlush(job);

        Job loaded = entityManager.find(Job.class, job.getId());
        assertThat(loaded.getScenario()).isNotNull();
        assertThat(loaded.getCallbackUrl()).isEqualTo("https://callback.com/webhook");
        assertThat(loaded.getError()).isEqualTo("some error");
        assertThat(loaded.getLockedBy()).isEqualTo("worker-1");
    }
}
