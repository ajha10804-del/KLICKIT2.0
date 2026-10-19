package com.klickit.user.controller;

import com.klickit.common.dto.ApiResponse;
import com.klickit.user.dto.AuthResponse;
import com.klickit.user.dto.LoginRequest;
import com.klickit.user.dto.RegisterRequest;
import com.klickit.user.service.UserService;
import com.klickit.user.dto.RegistrationOtpRequest;
import com.klickit.user.dto.RegistrationVerifyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration and authentication APIs")
public class AuthController {

    private final UserService userService;

    @Operation(summary = "Register a new user (Disabled)", description = "Direct registration without email verification is disabled. Please use /auth/register/request-otp.")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request) {
        throw new IllegalStateException("Direct registration without email verification is disabled. Please verify your email via /auth/register/request-otp.");
    }

    @Operation(summary = "Request registration OTP", description = "Pre-validates registration details and dispatches a 6-digit OTP to the customer's email")
    @PostMapping("/register/request-otp")
    public ResponseEntity<ApiResponse<Void>> requestRegistrationOtp(
            @Valid @RequestBody RegistrationOtpRequest request) {
        userService.requestRegistrationOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Verification code sent to your email", null));
    }

    @Operation(summary = "Verify registration OTP", description = "Verifies email OTP, persists customer account with BCrypt password, and returns authenticated session")
    @PostMapping("/register/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyRegistrationOtp(
            @Valid @RequestBody RegistrationVerifyRequest request) {
        AuthResponse response = userService.verifyRegistrationOtp(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }

    @Operation(summary = "Authenticate user", description = "Login with email and password to obtain a JWT bearer token")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }
}
