package com.webevaluator.auth.repository;

import com.webevaluator.core.domain.Credential;
import com.webevaluator.core.domain.CredentialType;
import com.webevaluator.core.domain.Role;
import com.webevaluator.core.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CredentialRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CredentialRepository credentialRepository;

    @Test
    void shouldFindCredentialsByOwnerId() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        Credential cred1 = Credential.builder()
                .owner(user)
                .name("cred-1")
                .type(CredentialType.BASIC_AUTH)
                .payloadEncrypted("encrypted1")
                .build();
        entityManager.persistAndFlush(cred1);

        Credential cred2 = Credential.builder()
                .owner(user)
                .name("cred-2")
                .type(CredentialType.BEARER)
                .payloadEncrypted("encrypted2")
                .build();
        entityManager.persistAndFlush(cred2);

        User otherUser = User.builder()
                .username("otheruser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(otherUser);

        Credential otherCred = Credential.builder()
                .owner(otherUser)
                .name("other-cred")
                .type(CredentialType.FORM_LOGIN)
                .payloadEncrypted("encrypted3")
                .build();
        entityManager.persistAndFlush(otherCred);

        List<Credential> credentials = credentialRepository.findByOwnerId(user.getId());

        assertThat(credentials).hasSize(2);
        assertThat(credentials).extracting(Credential::getName).containsExactlyInAnyOrder("cred-1", "cred-2");
    }

    @Test
    void shouldReturnEmptyListWhenNoCredentialsForOwner() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        List<Credential> credentials = credentialRepository.findByOwnerId(user.getId());
        assertThat(credentials).isEmpty();
    }

    @Test
    void shouldPersistCredentialWithTimestamps() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        Credential credential = Credential.builder()
                .owner(user)
                .name("test-cred")
                .type(CredentialType.FORM_LOGIN)
                .payloadEncrypted("encrypted")
                .build();
        entityManager.persistAndFlush(credential);

        Credential loaded = entityManager.find(Credential.class, credential.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldUpdateCredentialTimestamp() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        entityManager.persistAndFlush(user);

        Credential credential = Credential.builder()
                .owner(user)
                .name("test-cred")
                .type(CredentialType.BEARER)
                .payloadEncrypted("encrypted")
                .build();
        entityManager.persistAndFlush(credential);

        UUID id = credential.getId();
        entityManager.flush();
        var originalUpdatedAt = entityManager.find(Credential.class, id).getUpdatedAt();

        Credential loaded = entityManager.find(Credential.class, id);
        loaded.setName("updated-cred");
        entityManager.flush();

        Credential updated = entityManager.find(Credential.class, id);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }
}
