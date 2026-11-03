package com.fooddelivery.userservice.config;

import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.entity.User;
import com.fooddelivery.userservice.entity.UserStatus;
import com.fooddelivery.userservice.repository.UserRepository;
import com.fooddelivery.userservice.service.UserInputNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the first PLATFORM_ADMIN at startup, because nobody can self-register as an admin.
 * Credentials come from BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD (never from source code).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.BootstrapAdmin bootstrapAdmin = appProperties.bootstrapAdmin();

        if (!StringUtils.hasText(bootstrapAdmin.email()) || !StringUtils.hasText(bootstrapAdmin.password())) {
            log.info("Bootstrap admin is not configured. Skipping.");
            return;
        }

        String email = UserInputNormalizer.normalizeEmail(bootstrapAdmin.email());
        if (userRepository.existsByEmail(email)) {
            log.info("Bootstrap admin already exists. Skipping.");
            return;
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(bootstrapAdmin.password()));
        admin.setFullName(bootstrapAdmin.fullName());
        admin.setRole(Role.PLATFORM_ADMIN);
        admin.setStatus(UserStatus.ACTIVE);

        User saved = userRepository.save(admin);
        log.info("Bootstrap platform admin created. userId={}", saved.getId());
    }
}
