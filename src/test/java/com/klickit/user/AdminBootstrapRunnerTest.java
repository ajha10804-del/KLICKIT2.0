package com.klickit.user;

import com.klickit.user.bootstrap.AdminBootstrapRunner;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

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
    @DisplayName("Creates admin when user with email does not exist")
    void createsAdminWhenNoneExists() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Admin", "admin@example.com", "1234567890", "supersecret123");

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByPhone("1234567890")).thenReturn(false);
        when(passwordEncoder.encode("supersecret123")).thenReturn("encoded_supersecret123");

        runner.run(args);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRole()).isEqualTo(Role.ADMIN);
        assertThat(savedUser.isEnabled()).isTrue();
        assertThat(savedUser.getPassword()).isEqualTo("encoded_supersecret123");
        assertThat(savedUser.getPassword()).isNotEqualTo("supersecret123");
    }

    @Test
    @DisplayName("Creates owner admin when another admin already exists under different email")
    void createsOwnerAdminWhenAnotherAdminAlreadyExistsUnderDifferentEmail() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Owner Admin", "owner@klickit.in", "9876543210", "highEntropyPass123", false);

        when(userRepository.findByEmail("owner@klickit.in")).thenReturn(Optional.empty());
        when(userRepository.existsByPhone("9876543210")).thenReturn(false);
        when(passwordEncoder.encode("highEntropyPass123")).thenReturn("encoded_owner_pass");

        runner.run(args);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("owner@klickit.in");
        assertThat(savedUser.getRole()).isEqualTo(Role.ADMIN);
        assertThat(savedUser.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Skips when config is blank")
    void skipsWhenConfigBlank() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Admin", "", "", "");

        runner.run(args);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Skips when admin with email already exists and rotate is false")
    void skipsWhenAdminExistsAndRotateFalse() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Admin", "admin@example.com", "1234567890", "supersecret123", false);

        User existingAdmin = User.builder()
                .email("admin@example.com")
                .role(Role.ADMIN)
                .password("old_hash")
                .build();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(existingAdmin));

        runner.run(args);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rotates admin password when admin exists and rotate is true")
    void rotatesPasswordWhenAdminExistsAndRotateTrue() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Owner Admin", "owner@klickit.in", "9876543210", "newSecretPassword123", true);

        User existingAdmin = User.builder()
                .name("Old Name")
                .email("owner@klickit.in")
                .phone("1111111111")
                .role(Role.ADMIN)
                .password("old_hash")
                .build();
        existingAdmin.setId(UUID.randomUUID());

        when(userRepository.findByEmail("owner@klickit.in")).thenReturn(Optional.of(existingAdmin));
        when(passwordEncoder.encode("newSecretPassword123")).thenReturn("new_encoded_hash");

        runner.run(args);

        verify(passwordEncoder).encode("newSecretPassword123");
        verify(userRepository).save(existingAdmin);
        assertThat(existingAdmin.getPassword()).isEqualTo("new_encoded_hash");
        assertThat(existingAdmin.getName()).isEqualTo("Owner Admin");
        assertThat(existingAdmin.getPhone()).isEqualTo("9876543210");
        assertThat(existingAdmin.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Throws exception when rotate is true but user is not an ADMIN")
    void throwsWhenRotateTrueOnNonAdmin() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Customer", "customer@klickit.in", "9876543210", "newSecretPassword123", true);

        User existingCustomer = User.builder()
                .email("customer@klickit.in")
                .role(Role.CUSTOMER)
                .build();
        when(userRepository.findByEmail("customer@klickit.in")).thenReturn(Optional.of(existingCustomer));

        assertThatThrownBy(() -> runner.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exists but is not an ADMIN. Automatic role promotion is disallowed.");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws exception when password is shorter than 12 characters")
    void throwsOnPasswordShorterThan12() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Admin", "admin@example.com", "1234567890", "short");

        assertThatThrownBy(() -> runner.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be at least 12 characters");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws exception when new admin phone is already registered")
    void throwsWhenPhoneAlreadyUsed() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Admin", "admin@example.com", "1234567890", "supersecret123");

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByPhone("1234567890")).thenReturn(true);

        assertThatThrownBy(() -> runner.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("phone number is already registered to another account");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Preserves existing phone when rotating admin with blank phone in config")
    void preservesExistingPhoneWhenRotatingWithBlankPhone() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Updated Name", "owner@klickit.in", "   ", "newSecretPassword123", true);

        User existingAdmin = User.builder()
                .name("Old Name")
                .email("owner@klickit.in")
                .phone("9876543210")
                .role(Role.ADMIN)
                .password("old_hash")
                .enabled(true)
                .build();

        when(userRepository.findByEmail("owner@klickit.in")).thenReturn(Optional.of(existingAdmin));
        when(passwordEncoder.encode("newSecretPassword123")).thenReturn("new_encoded_hash");

        runner.run(args);

        verify(userRepository).save(existingAdmin);
        assertThat(existingAdmin.getPassword()).isEqualTo("new_encoded_hash");
        assertThat(existingAdmin.getPhone()).isEqualTo("9876543210");
        assertThat(existingAdmin.getName()).isEqualTo("Updated Name");
    }

    @Test
    @DisplayName("Preserves existing name when rotating admin with blank name in config")
    void preservesExistingNameWhenRotatingWithBlankName() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "", "owner@klickit.in", "9998887776", "newSecretPassword123", true);

        User existingAdmin = User.builder()
                .name("Permanent Owner")
                .email("owner@klickit.in")
                .phone("9876543210")
                .role(Role.ADMIN)
                .password("old_hash")
                .enabled(true)
                .build();

        when(userRepository.findByEmail("owner@klickit.in")).thenReturn(Optional.of(existingAdmin));
        when(passwordEncoder.encode("newSecretPassword123")).thenReturn("new_encoded_hash");

        runner.run(args);

        verify(userRepository).save(existingAdmin);
        assertThat(existingAdmin.getName()).isEqualTo("Permanent Owner");
        assertThat(existingAdmin.getPhone()).isEqualTo("9998887776");
    }

    @Test
    @DisplayName("Recovers disabled admin by setting enabled to true upon rotation")
    void recoversDisabledAdminOnRotation() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Owner Admin", "owner@klickit.in", "9876543210", "newSecretPassword123", true);

        User disabledAdmin = User.builder()
                .name("Owner Admin")
                .email("owner@klickit.in")
                .phone("9876543210")
                .role(Role.ADMIN)
                .password("old_hash")
                .enabled(false)
                .build();

        when(userRepository.findByEmail("owner@klickit.in")).thenReturn(Optional.of(disabledAdmin));
        when(passwordEncoder.encode("newSecretPassword123")).thenReturn("new_encoded_hash");

        runner.run(args);

        verify(userRepository).save(disabledAdmin);
        assertThat(disabledAdmin.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Throws exception when new admin phone is blank")
    void throwsWhenNewAdminPhoneIsBlank() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Admin", "admin@example.com", "   ", "supersecret123");

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runner.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("phone must not be blank for new admin creation");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Normalizes email by trimming and converting to lower case")
    void normalizesEmailTrimmingAndLowerCasing() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Admin", "  Owner.Admin@KlickIt.IN  ", "9876543210", "supersecret123", false);

        when(userRepository.findByEmail("owner.admin@klickit.in")).thenReturn(Optional.empty());
        when(userRepository.existsByPhone("9876543210")).thenReturn(false);
        when(passwordEncoder.encode("supersecret123")).thenReturn("encoded_pass");

        runner.run(args);

        verify(userRepository).findByEmail("owner.admin@klickit.in");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("owner.admin@klickit.in");
    }

    @Test
    @DisplayName("Throws exception when email is blank but password is provided")
    void throwsWhenEmailIsBlank() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, "Admin", "   ", "9876543210", "supersecret123", false);

        assertThatThrownBy(() -> runner.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email must not be blank");

        verify(userRepository, never()).save(any());
    }
}
