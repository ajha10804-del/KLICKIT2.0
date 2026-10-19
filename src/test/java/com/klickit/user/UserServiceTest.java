package com.klickit.user;

import com.klickit.common.exception.ResourceNotFoundException;
import com.klickit.config.JwtService;
import com.klickit.user.dto.AuthResponse;
import com.klickit.user.dto.LoginRequest;
import com.klickit.user.dto.UpdateProfileRequest;
import com.klickit.user.dto.UserProfileResponse;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import com.klickit.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, jwtService, authenticationManager);
    }

    @Test
    @DisplayName("login with mixed-case email normalizes to lowercase and authenticates")
    void login_mixedCaseEmail_normalizesAndAuthenticates() {
        User user = User.builder()
                .name("Admin User")
                .email("admin@klickit.test")
                .role(Role.ADMIN)
                .password("encoded")
                .build();

        when(userRepository.findByEmailIgnoreCase("admin@klickit.test")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("admin-token");

        LoginRequest request = new LoginRequest("  Admin@KlickIt.Test  ", "Password123!");
        AuthResponse response = userService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("admin-token");
        assertThat(response.getEmail()).isEqualTo("admin@klickit.test");
        assertThat(response.getRole()).isEqualTo(Role.ADMIN);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository).findByEmailIgnoreCase("admin@klickit.test");
    }

    @Test
    @DisplayName("login with bad credentials throws BadCredentialsException")
    void login_badCredentials_throws() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest request = new LoginRequest("user@test.com", "wrongpass");

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("getProfile retrieves user by email ignoring case")
    void getProfile_returnsProfile() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .name("Customer One")
                .email("cust@test.com")
                .phone("9876543210")
                .role(Role.CUSTOMER)
                .build();
        user.setId(userId);

        when(userRepository.findByEmailIgnoreCase("cust@test.com")).thenReturn(Optional.of(user));

        UserProfileResponse profile = userService.getProfile("  CUST@test.com ");

        assertThat(profile.getId()).isEqualTo(userId);
        assertThat(profile.getName()).isEqualTo("Customer One");
        assertThat(profile.getEmail()).isEqualTo("cust@test.com");
        assertThat(profile.getPhone()).isEqualTo("9876543210");
        assertThat(profile.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    @DisplayName("updateProfile updates name and phone")
    void updateProfile_updatesNameAndPhone() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .name("Old Name")
                .email("user@test.com")
                .phone("9000000000")
                .role(Role.CUSTOMER)
                .build();
        user.setId(userId);

        when(userRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.existsByPhoneAndIdNot("9876543210", userId)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .name("New Name")
                .phone("9876543210")
                .build();

        UserProfileResponse updated = userService.updateProfile("user@test.com", request);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getPhone()).isEqualTo("9876543210");
    }

    @Test
    @DisplayName("updateProfile throws when phone number is already registered to another user")
    void updateProfile_duplicatePhone_throws() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .name("User")
                .email("user@test.com")
                .role(Role.CUSTOMER)
                .build();
        user.setId(userId);

        when(userRepository.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.existsByPhoneAndIdNot("9876543210", userId)).thenReturn(true);

        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .phone("9876543210")
                .build();

        assertThatThrownBy(() -> userService.updateProfile("user@test.com", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("getProfile throws ResourceNotFoundException if user does not exist")
    void getProfile_notFound_throws() {
        when(userRepository.findByEmailIgnoreCase("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile("unknown@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
