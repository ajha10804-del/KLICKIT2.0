package com.klickit.user;

import com.klickit.user.bootstrap.AdminBootstrapRunner;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments args;

    @Test
    void createsAdminWhenNoneExists() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Admin", "admin@example.com", "1234567890", "supersecret123");

        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("1234567890")).thenReturn(false);
        when(passwordEncoder.encode("supersecret123")).thenReturn("encoded_supersecret123");

        runner.run(args);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRole()).isEqualTo(Role.ADMIN);
        assertThat(savedUser.getPassword()).isEqualTo("encoded_supersecret123");
        assertThat(savedUser.getPassword()).isNotEqualTo("supersecret123");
    }

    @Test
    void skipsWhenConfigBlank() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Admin", "", "", "");

        runner.run(args);

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).existsByRole(any());
    }

    @Test
    void skipsWhenAdminExists() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Admin", "admin@example.com", "1234567890", "supersecret123");

        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        runner.run(args);

        verify(userRepository, never()).save(any());
    }

    @Test
    void throwsOnPasswordShorterThan12() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Admin", "admin@example.com", "1234567890", "short");

        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> runner.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be at least 12 characters");

        verify(userRepository, never()).save(any());
    }

    @Test
    void throwsWhenEmailOrPhoneAlreadyUsed() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Admin", "admin@example.com", "1234567890", "supersecret123");

        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);

        assertThatThrownBy(() -> runner.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }
}
