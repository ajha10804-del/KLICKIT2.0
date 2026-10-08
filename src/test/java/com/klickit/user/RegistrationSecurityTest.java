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
    @DisplayName("Register without role field defaults to CUSTOMER")
    void register_withoutRole_assignsCustomer() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Alice");
        request.setEmail("alice@test.com");
        request.setPhone("1111111111");
        request.setPassword("password123");
        request.setRole(null);

        when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(userRepository.existsByPhone("1111111111")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        AuthResponse response = userService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(response.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    @DisplayName("Register with role=ADMIN is forced to CUSTOMER (prevents privilege escalation)")
    void register_withRoleAdmin_forcesCustomer() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Jay");
        request.setEmail("jay@test.com");
        request.setPhone("2222222222");
        request.setPassword("password123");
        request.setRole(Role.ADMIN);

        when(userRepository.existsByEmail("jay@test.com")).thenReturn(false);
        when(userRepository.existsByPhone("2222222222")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        AuthResponse response = userService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        // Verifies the persisted entity role is CUSTOMER, never ADMIN
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(response.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    @DisplayName("Register with role=DELIVERY_PARTNER is forced to CUSTOMER")
    void register_withRoleDeliveryPartner_forcesCustomer() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Bob");
        request.setEmail("bob@test.com");
        request.setPhone("3333333333");
        request.setPassword("password123");
        request.setRole(Role.DELIVERY_PARTNER);

        when(userRepository.existsByEmail("bob@test.com")).thenReturn(false);
        when(userRepository.existsByPhone("3333333333")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        AuthResponse response = userService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(response.getRole()).isEqualTo(Role.CUSTOMER);
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
