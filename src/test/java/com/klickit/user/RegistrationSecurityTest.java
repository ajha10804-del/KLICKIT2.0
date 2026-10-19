package com.klickit.user;

import com.klickit.config.JwtService;
import com.klickit.user.dto.AuthResponse;
import com.klickit.user.dto.LoginRequest;
import com.klickit.user.dto.RegisterRequest;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import com.klickit.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(passwordEncoder.encode(any())).thenReturn("hashedPassword");
        org.mockito.Mockito.lenient().when(jwtService.generateToken(any())).thenReturn("mock-jwt-token");
    }

    @Test
    @DisplayName("Calling legacy register throws IllegalStateException to enforce OTP email verification")
    void register_directUnverified_throwsIllegalStateException() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Alice");
        request.setEmail("alice@test.com");
        request.setPhone("1111111111");
        request.setPassword("password123");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Direct registration without email verification is disabled");
    }

    @Test
    @DisplayName("Deactivated user (enabled = false) fails authentication with DisabledException")
    void deactivatedUser_failsLogin() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new org.springframework.security.authentication.DisabledException("User is disabled"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                userService.login(new LoginRequest("disabled@klickit.com", "pass123")))
                .isInstanceOf(org.springframework.security.authentication.DisabledException.class)
                .hasMessageContaining("User is disabled");
    }

    @Test
    @DisplayName("Existing ADMIN users preserve their ADMIN role upon login")
    void existingAdminUser_remainsAdmin() {
        User adminUser = User.builder()
                .name("Super Admin")
                .email("admin@klickit.com")
                .phone("9999999999")
                .password("hashedPassword")
                .role(Role.ADMIN)
                .build();
        adminUser.setId(UUID.randomUUID());

        when(userRepository.findByEmail("admin@klickit.com")).thenReturn(Optional.of(adminUser));

        AuthResponse response = userService.login(new LoginRequest("admin@klickit.com", "adminPass123"));

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Existing DELIVERY_PARTNER users preserve their DELIVERY_PARTNER role upon login")
    void existingDeliveryPartner_remainsDeliveryPartner() {
        User partnerUser = User.builder()
                .name("Speedy Rider")
                .email("partner@klickit.com")
                .phone("8888888888")
                .password("hashedPassword")
                .role(Role.DELIVERY_PARTNER)
                .build();
        partnerUser.setId(UUID.randomUUID());

        when(userRepository.findByEmail("partner@klickit.com")).thenReturn(Optional.of(partnerUser));

        AuthResponse response = userService.login(new LoginRequest("partner@klickit.com", "partnerPass123"));

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        assertThat(response.getRole()).isEqualTo(Role.DELIVERY_PARTNER);
    }

    @Test
    @DisplayName("AuthResponse includes customer phone number on login")
    void authResponse_includesCustomerPhone() {
        User customer = User.builder()
                .name("Karan")
                .email("karan@example.com")
                .phone("9820098200")
                .password("hashedPassword")
                .role(Role.CUSTOMER)
                .build();
        customer.setId(UUID.randomUUID());

        when(userRepository.findByEmail("karan@example.com")).thenReturn(Optional.of(customer));

        AuthResponse loginResponse = userService.login(new LoginRequest("karan@example.com", "pass123"));
        assertThat(loginResponse.getPhone()).isEqualTo("9820098200");
    }
}
