package com.webevaluator.core.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UserTest {
    @Test
    void shouldSetDefaultValuesOnCreate() {
        User user = User.builder()
                .username("test")
                .passwordHash("hash")
                .role(Role.USER)
                .build();

        user.onCreate();

        assertThat(user.getActive()).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }
}