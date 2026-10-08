package com.klickit.user.bootstrap;

import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminName;
    private final String adminEmail;
    private final String adminPhone;
    private final String adminPassword;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${klickit.bootstrap.admin.name:KlickIt Admin}") String adminName,
            @Value("${klickit.bootstrap.admin.email:}") String adminEmail,
            @Value("${klickit.bootstrap.admin.phone:}") String adminPhone,
            @Value("${klickit.bootstrap.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminName = adminName;
        this.adminEmail = adminEmail != null ? adminEmail.trim() : "";
        this.adminPhone = adminPhone != null ? adminPhone.trim() : "";
        this.adminPassword = adminPassword != null ? adminPassword.trim() : "";
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean isEmailBlank = adminEmail.isEmpty();
        boolean isPasswordBlank = adminPassword.isEmpty();
        
        if (isEmailBlank && isPasswordBlank) {
            log.info("Admin bootstrap not configured");
            return;
        }

        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("Admin already exists, skipping bootstrap");
            return;
        }

        boolean isPhoneBlank = adminPhone.isEmpty();
        if (isEmailBlank || isPhoneBlank || adminPassword.length() < 12) {
            throw new IllegalStateException("Admin bootstrap failed: email and phone must not be blank, and password must be at least 12 characters.");
        }

        if (userRepository.existsByEmail(adminEmail) || userRepository.existsByPhone(adminPhone)) {
            throw new IllegalStateException("Admin bootstrap failed: A user with this email or phone already exists. Existing accounts are NOT promoted automatically.");
        }

        User admin = User.builder()
                .name(adminName)
                .email(adminEmail)
                .phone(adminPhone)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .build();
        
        userRepository.save(admin);
        log.info("Admin bootstrap successful. Created ADMIN with email: {}", adminEmail);
    }
}
