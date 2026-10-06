package com.webevaluator.orchestrator.repository;

import com.webevaluator.core.domain.Role;
import com.webevaluator.core.domain.Scenario;
import com.webevaluator.core.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ScenarioRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Test
    void shouldFindScenariosByOwnerId() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        Scenario scenario1 = Scenario.builder()
                .owner(user)
                .name("scenario-1")
                .content("step1: open url")
                .build();
        entityManager.persistAndFlush(scenario1);

        Scenario scenario2 = Scenario.builder()
                .owner(user)
                .name("scenario-2")
                .content("step1: click button")
                .build();
        entityManager.persistAndFlush(scenario2);

        User otherUser = User.builder()
                .username("otheruser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(otherUser);

        Scenario otherScenario = Scenario.builder()
                .owner(otherUser)
                .name("other-scenario")
                .content("step1: submit form")
                .build();
        entityManager.persistAndFlush(otherScenario);

        List<Scenario> scenarios = scenarioRepository.findByOwnerId(user.getId());

        assertThat(scenarios).hasSize(2);
        assertThat(scenarios).extracting(Scenario::getName).containsExactlyInAnyOrder("scenario-1", "scenario-2");
    }

    @Test
    void shouldReturnEmptyListWhenNoScenariosForOwner() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        List<Scenario> scenarios = scenarioRepository.findByOwnerId(user.getId());
        assertThat(scenarios).isEmpty();
    }

    @Test
    void shouldPersistScenarioWithTimestamps() {
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
                .content("test content")
                .build();
        entityManager.persistAndFlush(scenario);

        Scenario loaded = entityManager.find(Scenario.class, scenario.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldUpdateScenarioTimestamp() {
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
                .content("original content")
                .build();
        entityManager.persistAndFlush(scenario);

        UUID id = scenario.getId();
        entityManager.flush();
        var originalUpdatedAt = entityManager.find(Scenario.class, id).getUpdatedAt();

        Scenario loaded = entityManager.find(Scenario.class, id);
        loaded.setContent("updated content");
        entityManager.flush();

        Scenario updated = entityManager.find(Scenario.class, id);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }

    @Test
    void shouldPersistScenarioWithLongContent() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        String longContent = "step1: open https://example.com\nstep2: click login\nstep3: fill form\n" +
                "step4: submit\nstep5: verify success";
        Scenario scenario = Scenario.builder()
                .owner(user)
                .name("long-scenario")
                .content(longContent)
                .build();
        entityManager.persistAndFlush(scenario);

        Scenario loaded = entityManager.find(Scenario.class, scenario.getId());
        assertThat(loaded.getContent()).isEqualTo(longContent);
    }
}
