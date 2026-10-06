package com.webevaluator.auth.repository;

import com.webevaluator.core.domain.Role;
import com.webevaluator.core.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldFindByUsername() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        Optional<User> found = userRepository.findByUsername("testuser");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldReturnFalseWhenUserNotFound() {
        boolean exists = userRepository.existsByUsername("nonexistent");
        assertThat(exists).isFalse();
    }

    @Test
    void shouldReturnTrueWhenUserExists() {
        User user = User.builder()
                .username("existinguser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        boolean exists = userRepository.existsByUsername("existinguser");
        assertThat(exists).isTrue();
    }

    @Test
    void shouldPersistAndLoadUserWithDefaults() {
        User user = User.builder()
                .username("defaultuser")
                .passwordHash("hashed")
                .role(Role.ADMIN)
                .build();
        entityManager.persistAndFlush(user);

        User loaded = entityManager.find(User.class, user.getId());
        assertThat(loaded.getActive()).isTrue();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldUpdateUserTimestamp() {
        User user = User.builder()
                .username("timestampuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        OffsetDateTime originalUpdatedAt = entityManager.find(User.class, user.getId()).getUpdatedAt();

        user.setUsername("updateduser");
        entityManager.flush();

        User loaded = entityManager.find(User.class, user.getId());
        assertThat(loaded.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }
}
