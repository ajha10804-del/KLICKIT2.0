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
@Tag(name = "Email OTP Authentication", description = "Passwordless sign-in with a one-time code sent by email")
public class OtpAuthController {

    private final OtpService otpService;

    @Operation(summary = "Send a sign-in code",
            description = "Emails a 6-digit code. The response is identical whether or not the email already has an account.")
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<Void>> requestCode(@Valid @RequestBody OtpRequest request) {
        otpService.requestCode(request.getEmail());
        return ResponseEntity.ok(ApiResponse.<Void>success("If the email is valid, a sign-in code has been sent", null));
    }

    @Operation(summary = "Verify the code and sign in",
            description = "Returns a JWT and the account role (CUSTOMER, ADMIN or DELIVERY_PARTNER). Unknown emails become CUSTOMER accounts.")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<AuthResponse>> verify(@Valid @RequestBody OtpVerifyRequest request) {
        AuthResponse response = otpService.verifyCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }
}
