package com.klickit.user.bootstrap;

import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final boolean rotate;

    @Autowired
    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${klickit.bootstrap.admin.name:KlickIt Admin}") String adminName,
            @Value("${klickit.bootstrap.admin.email:}") String adminEmail,
            @Value("${klickit.bootstrap.admin.phone:}") String adminPhone,
            @Value("${klickit.bootstrap.admin.password:}") String adminPassword,
            @Value("${klickit.bootstrap.admin.rotate:false}") boolean rotate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminName = adminName;
        this.adminEmail = adminEmail != null ? adminEmail.trim().toLowerCase() : "";
        this.adminPhone = adminPhone != null ? adminPhone.trim() : "";
        this.adminPassword = adminPassword != null ? adminPassword.trim() : "";
        this.rotate = rotate;
    }

    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            String adminName,
            String adminEmail,
            String adminPhone,
            String adminPassword) {
        this(userRepository, passwordEncoder, adminName, adminEmail, adminPhone, adminPassword, false);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean isEmailBlank = adminEmail.isEmpty();
        boolean isPasswordBlank = adminPassword.isEmpty();
        
        if (isEmailBlank && isPasswordBlank) {
            log.info("Admin bootstrap not configured");
            log.info("Admin bootstrap outcome: [NOT_CONFIGURED]");
            return;
        }

        if (isEmailBlank || adminPassword.length() < 12) {
            throw new IllegalStateException("Admin bootstrap failed: email must not be blank, and password must be at least 12 characters.");
        }

        java.util.Optional<User> existingUserOpt = userRepository.findByEmail(adminEmail);

        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            if (rotate) {
                if (existingUser.getRole() != Role.ADMIN) {
                    throw new IllegalStateException("Admin bootstrap failed: user with email " + adminEmail + " exists but is not an ADMIN. Automatic role promotion is disallowed.");
                }
                existingUser.setPassword(passwordEncoder.encode(adminPassword));
                if (!adminPhone.isEmpty()) {
                    existingUser.setPhone(adminPhone);
                }
                if (!adminName.isEmpty()) {
                    existingUser.setName(adminName);
                }
                existingUser.setEnabled(true);
                userRepository.save(existingUser);
                log.info("Admin password rotated successfully for: {}", adminEmail);
                log.info("Admin bootstrap outcome: [ROTATED] for email: {}", adminEmail);
                return;
            } else {
                log.warn("Admin with email {} already exists; configured password was NOT applied. Set BOOTSTRAP_ADMIN_ROTATE=true to change it.", adminEmail);
                log.warn("Admin bootstrap outcome: [SKIPPED] for email: {}", adminEmail);
                return;
            }
        }

        boolean isPhoneBlank = adminPhone.isEmpty();
        if (isPhoneBlank) {
            throw new IllegalStateException("Admin bootstrap failed: phone must not be blank for new admin creation.");
        }

        if (userRepository.existsByPhone(adminPhone)) {
            throw new IllegalStateException("Admin bootstrap failed: phone number is already registered to another account.");
        }

        User admin = User.builder()
                .name(adminName)
                .email(adminEmail)
                .phone(adminPhone)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        
        userRepository.save(admin);
        log.info("Admin bootstrap successful. Created ADMIN with email: {}", adminEmail);
        log.info("Admin bootstrap outcome: [CREATED] for email: {}", adminEmail);
    }
}
