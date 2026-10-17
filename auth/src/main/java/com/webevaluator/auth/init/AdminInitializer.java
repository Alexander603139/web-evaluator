package com.webevaluator.auth.init;

import com.webevaluator.auth.repository.UserRepository;
import com.webevaluator.core.domain.Role;
import com.webevaluator.core.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureDefaultAdmin() {
        if (userRepository.count() > 0) {
            log.info("Users table is not empty, skipping admin initialization");
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("ADMIN_PASSWORD env is empty, skipping admin initialization");
            return;
        }
        User admin = User.builder()
                .username(adminUsername)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .active(true)
                .build();
        userRepository.save(admin);
        log.info("Default admin '{}' created", adminUsername);
    }
}