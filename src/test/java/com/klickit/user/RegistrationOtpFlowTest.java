package com.klickit.user;

import com.klickit.common.exception.InvalidOtpException;
import com.klickit.config.JwtService;
import com.klickit.user.dto.AuthResponse;
import com.klickit.user.dto.RegistrationOtpRequest;
import com.klickit.user.dto.RegistrationVerifyRequest;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import com.klickit.user.service.OtpService;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Customer Registration with Email OTP Tests")
class RegistrationOtpFlowTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private OtpService otpService;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        lenient().when(passwordEncoder.encode(any())).thenReturn("hashed_password_123");
        lenient().when(jwtService.generateToken(any())).thenReturn("valid.jwt.token");
    }

    @Test
    @DisplayName("requestRegistrationOtp calls OtpService when input is valid and unverified")
    void requestRegistrationOtp_success() {
        RegistrationOtpRequest req = RegistrationOtpRequest.builder()
                .name("New User")
                .email("newuser@example.com")
                .phone("9876543210")
                .password("securePassword123")
                .build();

        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("9876543210")).thenReturn(false);

        userService.requestRegistrationOtp(req);

        verify(otpService).requestOtp("newuser@example.com", OtpService.PURPOSE_REGISTRATION);
    }

    @Test
    @DisplayName("requestRegistrationOtp throws when email is already registered")
    void requestRegistrationOtp_emailExists_throws() {
        RegistrationOtpRequest req = RegistrationOtpRequest.builder()
                .name("Existing User")
                .email("existing@example.com")
                .phone("9876543210")
                .password("securePassword123")
                .build();

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.requestRegistrationOtp(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("An account with this email already exists");

        verify(otpService, never()).requestOtp(any());
    }

    @Test
    @DisplayName("requestRegistrationOtp throws when phone is already registered")
    void requestRegistrationOtp_phoneExists_throws() {
        RegistrationOtpRequest req = RegistrationOtpRequest.builder()
                .name("User")
                .email("new@example.com")
                .phone("9876543210")
                .password("securePassword123")
                .build();

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("9876543210")).thenReturn(true);

        assertThatThrownBy(() -> userService.requestRegistrationOtp(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Phone number already registered to another account");

        verify(otpService, never()).requestOtp(any());
    }

    @Test
    @DisplayName("verifyRegistrationOtp verifies code, hashes password, saves CUSTOMER, and returns JWT")
    void verifyRegistrationOtp_success() {
        RegistrationVerifyRequest req = RegistrationVerifyRequest.builder()
                .name("Alice Wonder")
                .email("alice@wonder.com")
                .phone("9876543211")
                .password("plainTextPass")
                .code("123456")
                .role(Role.ADMIN) // Attempted escalation
                .build();

        when(userRepository.existsByEmail("alice@wonder.com")).thenReturn(false);
        when(userRepository.existsByPhone("9876543211")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        AuthResponse resp = userService.verifyRegistrationOtp(req);

        // 1. Verify OTP was verified & consumed
        verify(otpService).verifyAndConsumeOtp("alice@wonder.com", "123456", OtpService.PURPOSE_REGISTRATION);

        // 2. Verify password was encoded
        verify(passwordEncoder).encode("plainTextPass");

        // 3. Verify user entity saved with Role.CUSTOMER strictly
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPassword()).isEqualTo("hashed_password_123");
        assertThat(saved.getEmail()).isEqualTo("alice@wonder.com");

        // 4. Verify AuthResponse
        assertThat(resp.getToken()).isEqualTo("valid.jwt.token");
        assertThat(resp.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    @DisplayName("verifyRegistrationOtp throws when OTP verification fails")
    void verifyRegistrationOtp_invalidCode_throws() {
        RegistrationVerifyRequest req = RegistrationVerifyRequest.builder()
                .name("Alice")
                .email("alice@wonder.com")
                .phone("9876543211")
                .password("plainTextPass")
                .code("000000")
                .build();

        doThrow(new InvalidOtpException("Invalid or expired code"))
                .when(otpService).verifyAndConsumeOtp("alice@wonder.com", "000000", OtpService.PURPOSE_REGISTRATION);

        assertThatThrownBy(() -> userService.verifyRegistrationOtp(req))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("Invalid or expired code");

        verify(userRepository, never()).save(any());
    }
}
