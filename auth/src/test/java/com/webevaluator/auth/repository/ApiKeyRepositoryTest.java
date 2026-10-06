package com.webevaluator.auth.repository;

import com.webevaluator.core.domain.ApiKey;
import com.webevaluator.core.domain.Role;
import com.webevaluator.core.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ApiKeyRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Test
    void shouldFindByKeyHash() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        ApiKey apiKey = ApiKey.builder()
                .user(user)
                .name("test-key")
                .keyHash("abc123hash")
                .keyPrefix("pk_12345678")
                .build();
        entityManager.persistAndFlush(apiKey);

        Optional<ApiKey> found = apiKeyRepository.findByKeyHash("abc123hash");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("test-key");
    }

    @Test
    void shouldReturnEmptyWhenKeyHashNotFound() {
        Optional<ApiKey> found = apiKeyRepository.findByKeyHash("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindActiveKeysByUserId() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        ApiKey activeKey = ApiKey.builder()
                .user(user)
                .name("active-key")
                .keyHash("active-hash")
                .keyPrefix("pk_active")
                .build();
        entityManager.persistAndFlush(activeKey);

        ApiKey revokedKey = ApiKey.builder()
                .user(user)
                .name("revoked-key")
                .keyHash("revoked-hash")
                .keyPrefix("pk_revoked")
                .revokedAt(OffsetDateTime.now())
                .build();
        entityManager.persistAndFlush(revokedKey);

        List<ApiKey> activeKeys = apiKeyRepository.findByUserIdAndRevokedAtIsNull(user.getId());

        assertThat(activeKeys).hasSize(1);
        assertThat(activeKeys.get(0).getName()).isEqualTo("active-key");
    }

    @Test
    void shouldPersistApiKeyWithCreatedAt() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        ApiKey apiKey = ApiKey.builder()
                .user(user)
                .name("test-key")
                .keyHash("test-hash")
                .keyPrefix("pk_test")
                .build();
        entityManager.persistAndFlush(apiKey);

        ApiKey loaded = entityManager.find(ApiKey.class, apiKey.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldPersistApiKeyWithLastUsedAt() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        OffsetDateTime now = OffsetDateTime.now();
        ApiKey apiKey = ApiKey.builder()
                .user(user)
                .name("test-key")
                .keyHash("test-hash")
                .keyPrefix("pk_test")
                .lastUsedAt(now)
                .build();
        entityManager.persistAndFlush(apiKey);

        ApiKey loaded = entityManager.find(ApiKey.class, apiKey.getId());
        assertThat(loaded.getLastUsedAt()).isEqualTo(now);
    }
}
