package com.klickit.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.user.bootstrap.AdminBootstrapRunner;
import com.klickit.user.dto.LoginRequest;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Admin Bootstrap Authentication & Rotation Verification Integration Tests")
public class AdminBootstrapAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (String email : createdEmails) {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        }
        createdEmails.clear();
    }

    private String generateUniqueEmail() {
        String email = "admin-" + UUID.randomUUID() + "@klickit.test";
        createdEmails.add(email);
        return email;
    }

    @Test
    @DisplayName("Case A: Fresh DB + bootstrap(email, password) -> POST /auth/login succeeds and role is ADMIN")
    void caseA_freshDb_bootstrapAdmin_loginSucceeds() throws Exception {
        String email = generateUniqueEmail();
        String password = "BootstrapPasswordA123";
        String phone = "91" + (System.currentTimeMillis() % 100000000L);

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                "Test Admin A",
                email,
                phone,
                password,
                false
        );
        runner.run(new DefaultApplicationArguments());

        LoginRequest loginRequest = new LoginRequest(email, password);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is(email)))
                .andExpect(jsonPath("$.data.role", is("ADMIN")))
                .andExpect(jsonPath("$.data.token", notNullValue()));
    }

    @Test
    @DisplayName("Case B: Existing admin with password A + bootstrap(password B, rotate=false) -> login with B fails, login with A succeeds")
    void caseB_existingAdmin_rotateFalse_skipKeepsPasswordA() throws Exception {
        String email = generateUniqueEmail();
        String passwordA = "InitialPasswordA123";
        String passwordB = "SecondPasswordB123";
        String phone = "92" + (System.currentTimeMillis() % 100000000L);

        // Pre-create admin with password A
        User initialAdmin = User.builder()
                .name("Existing Admin")
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(passwordA))
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        userRepository.save(initialAdmin);

        // Run bootstrap with password B and rotate=false
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                "Updated Name",
                email,
                phone,
                passwordB,
                false
        );
        runner.run(new DefaultApplicationArguments());

        // Login with password B must fail with 401 Unauthorized
        LoginRequest loginWithB = new LoginRequest(email, passwordB);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginWithB)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Invalid email or password")));

        // Login with original password A must succeed
        LoginRequest loginWithA = new LoginRequest(email, passwordA);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginWithA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.role", is("ADMIN")));
    }

    @Test
    @DisplayName("Case C: Existing admin with password A + bootstrap(password B, rotate=true) -> login with B succeeds, login with A fails")
    void caseC_existingAdmin_rotateTrue_updatesToPasswordB() throws Exception {
        String email = generateUniqueEmail();
        String passwordA = "OldPasswordA1234";
        String passwordB = "NewRotatedPasswordB123";
        String phone = "93" + (System.currentTimeMillis() % 100000000L);

        User initialAdmin = User.builder()
                .name("Original Admin")
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(passwordA))
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        userRepository.save(initialAdmin);

        // Run bootstrap with password B and rotate=true
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                "Rotated Admin",
                email,
                phone,
                passwordB,
                true
        );
        runner.run(new DefaultApplicationArguments());

        // Login with new password B must succeed
        LoginRequest loginWithB = new LoginRequest(email, passwordB);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginWithB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is(email)))
                .andExpect(jsonPath("$.data.role", is("ADMIN")));

        // Login with old password A must fail
        LoginRequest loginWithA = new LoginRequest(email, passwordA);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginWithA)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    @DisplayName("Case D1: Mixed-case email without spaces, e.g. 'Admin@Example.com', logs in -> 200, role ADMIN")
    void caseD1_mixedCaseEmailWithoutSpaces_logsInSuccessfully() throws Exception {
        String email = generateUniqueEmail();
        String password = "MixedCasePassword123";
        String phone = "94" + (System.currentTimeMillis() % 100000000L);

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                "Mixed Case Admin",
                email,
                phone,
                password,
                false
        );
        runner.run(new DefaultApplicationArguments());

        // Provide email with mixed case (no surrounding spaces)
        String mixedCaseEmail = email.toUpperCase();
        LoginRequest loginRequest = new LoginRequest(mixedCaseEmail, password);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is(email.toLowerCase())))
                .andExpect(jsonPath("$.data.role", is("ADMIN")));
    }

    @Test
    @DisplayName("Case D2: Email with surrounding spaces -> 400 Bad Request (documents @Email validation behavior)")
    void caseD2_emailWithSurroundingSpaces_rejectedByBeanValidation() throws Exception {
        String email = generateUniqueEmail();
        String password = "PaddedEmailPassword123";
        String phone = "95" + (System.currentTimeMillis() % 100000000L);

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                "Padded Email Admin",
                email,
                phone,
                password,
                false
        );
        runner.run(new DefaultApplicationArguments());

        // Provide email with surrounding spaces in login request
        String paddedEmail = "  " + email + "  ";
        LoginRequest loginRequest = new LoginRequest(paddedEmail, password);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("email: Email must be valid")));
    }

    @Test
    @DisplayName("Case E: Password with trailing whitespace in bootstrap config is trimmed, so login with trimmed value succeeds")
    void caseE_passwordWithTrailingWhitespace_trimmedAtBootstrap() throws Exception {
        String email = generateUniqueEmail();
        String trimmedPassword = "PasswordWithSpaces123";
        String untrimmedBootstrapPassword = trimmedPassword + "    ";
        String phone = "95" + (System.currentTimeMillis() % 100000000L);

        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository,
                passwordEncoder,
                "Whitespace Admin",
                email,
                phone,
                untrimmedBootstrapPassword,
                false
        );
        runner.run(new DefaultApplicationArguments());

        // 1. Login with the trimmed value succeeds
        LoginRequest loginTrimmed = new LoginRequest(email, trimmedPassword);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginTrimmed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.role", is("ADMIN")));

        // 2. Login with untrimmed value fails because login request does not trim passwords
        LoginRequest loginUntrimmed = new LoginRequest(email, untrimmedBootstrapPassword);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginUntrimmed)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }
}
