package com.klickit.user.service;

import com.klickit.common.exception.ResourceNotFoundException;
import com.klickit.config.JwtService;
import com.klickit.user.dto.AuthResponse;
import com.klickit.user.dto.LoginRequest;
import com.klickit.user.dto.RegisterRequest;
import com.klickit.user.dto.UpdateProfileRequest;
import com.klickit.user.dto.UserProfileResponse;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import com.klickit.user.dto.RegistrationOtpRequest;
import com.klickit.user.dto.RegistrationVerifyRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final OtpService otpService;

    @Autowired
    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            @Lazy OtpService otpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.otpService = otpService;
    }

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager) {
        this(userRepository, passwordEncoder, jwtService, authenticationManager, null);
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";
        return userRepository.findByEmail(normalizedEmail)
                .or(() -> userRepository.findByEmailIgnoreCase(normalizedEmail))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        throw new IllegalStateException("Direct registration without email verification is disabled. Please verify your email via /auth/register/request-otp.");
    }

    public void requestRegistrationOtp(RegistrationOtpRequest request) {
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String trimmedPhone = request.getPhone() != null ? request.getPhone().trim() : "";

        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException("An account with this email already exists");
        }
        if (!trimmedPhone.isBlank() && userRepository.existsByPhone(trimmedPhone)) {
            throw new IllegalStateException("Phone number already registered to another account");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        if (otpService == null) {
            throw new IllegalStateException("OTP service is currently unavailable");
        }
        otpService.requestOtp(normalizedEmail, OtpService.PURPOSE_REGISTRATION);
    }

    @Transactional
    public AuthResponse verifyRegistrationOtp(RegistrationVerifyRequest request) {
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String trimmedPhone = request.getPhone() != null ? request.getPhone().trim() : null;

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException("An account with this email already exists");
        }
        if (trimmedPhone != null && !trimmedPhone.isEmpty() && userRepository.existsByPhone(trimmedPhone)) {
            throw new IllegalStateException("Phone number already registered to another account");
        }

        if (otpService == null) {
            throw new IllegalStateException("OTP service is currently unavailable");
        }
        otpService.verifyAndConsumeOtp(normalizedEmail, request.getCode(), OtpService.PURPOSE_REGISTRATION);

        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .phone(trimmedPhone)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER) // Strictly enforce CUSTOMER role (prevents escalation)
                .build();

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved);

        return AuthResponse.builder()
                .token(token)
                .userId(saved.getId())
                .name(saved.getName())
                .email(saved.getEmail())
                .phone(saved.getPhone())
                .role(saved.getRole())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizedEmail,
                        request.getPassword()));

        User user = userRepository.findByEmail(normalizedEmail)
                .or(() -> userRepository.findByEmailIgnoreCase(normalizedEmail))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .build();
    }

    public UserProfileResponse getProfile(String email) {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";
        User user = userRepository.findByEmail(normalizedEmail)
                .or(() -> userRepository.findByEmailIgnoreCase(normalizedEmail))
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", normalizedEmail));

        return UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .build();
    }

    @Transactional
    public UserProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";
        User user = userRepository.findByEmail(normalizedEmail)
                .or(() -> userRepository.findByEmailIgnoreCase(normalizedEmail))
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", normalizedEmail));

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName().trim());
        }

        if (request.getPhone() != null) {
            String trimmedPhone = request.getPhone().trim();
            if (!trimmedPhone.isEmpty()) {
                if (userRepository.existsByPhoneAndIdNot(trimmedPhone, user.getId())) {
                    throw new IllegalStateException("Phone number already registered to another account");
                }
                user.setPhone(trimmedPhone);
            }
        }

        User saved = userRepository.save(user);
        return UserProfileResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .email(saved.getEmail())
                .phone(saved.getPhone())
                .role(saved.getRole())
                .build();
    }
}
