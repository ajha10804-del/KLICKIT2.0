package com.klickit.user.controller;

import com.klickit.common.dto.ApiResponse;
import com.klickit.user.dto.AuthResponse;
import com.klickit.user.dto.OtpRequest;
import com.klickit.user.dto.OtpVerifyRequest;
import com.klickit.user.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/otp")
@RequiredArgsConstructor
@Tag(name = "OTP Authentication", description = "Passwordless OTP authentication endpoints")
public class OtpAuthController {

    private final OtpService otpService;

    @Operation(summary = "Request OTP", description = "Send a 6-digit one-time password to the specified email")
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<Void>> requestOtp(@Valid @RequestBody OtpRequest request) {
        otpService.requestOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("If the email is valid, a sign-in code has been sent", null));
    }

    @Operation(summary = "Verify OTP", description = "Verify a 6-digit one-time password and authenticate user")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        AuthResponse response = otpService.verifyOtp(request.getEmail(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }
}
