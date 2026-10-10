package com.klickit.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.common.exception.GlobalExceptionHandler;
import com.klickit.common.exception.InvalidOtpException;
import com.klickit.common.exception.OtpRateLimitException;
import com.klickit.config.JwtAuthenticationEntryPoint;
import com.klickit.config.JwtAuthenticationFilter;
import com.klickit.config.JwtService;
import com.klickit.config.SecurityConfig;
import com.klickit.user.controller.OtpAuthController;
import com.klickit.user.dto.AuthResponse;
import com.klickit.user.dto.OtpRequest;
import com.klickit.user.dto.OtpVerifyRequest;
import com.klickit.user.entity.Role;
import com.klickit.user.service.OtpService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OtpAuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
class OtpAuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OtpService otpService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /auth/otp/request with valid email succeeds with 200 OK")
    void requestOtp_validEmail_succeeds() throws Exception {
        OtpRequest request = new OtpRequest("user@example.com");
        doNothing().when(otpService).requestOtp("user@example.com");

        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If the email is valid, a sign-in code has been sent"));
    }

    @Test
    @DisplayName("POST /auth/otp/request with invalid email returns 400 Bad Request")
    void requestOtp_invalidEmail_returnsBadRequest() throws Exception {
        OtpRequest request = new OtpRequest("not-an-email");

        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /auth/otp/request when rate-limited returns 429 Too Many Requests")
    void requestOtp_rateLimited_returns429() throws Exception {
        OtpRequest request = new OtpRequest("user@example.com");
        doThrow(new OtpRateLimitException("Please wait before requesting another sign-in code"))
                .when(otpService).requestOtp("user@example.com");

        mockMvc.perform(post("/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Please wait before requesting another sign-in code"));
    }

    @Test
    @DisplayName("POST /auth/otp/verify with valid code returns 200 OK and JWT auth response")
    void verifyOtp_validCode_returnsAuthResponse() throws Exception {
        OtpVerifyRequest request = new OtpVerifyRequest("user@example.com", "123456");
        AuthResponse response = AuthResponse.builder()
                .token("jwt-token-123")
                .userId(UUID.randomUUID())
                .name("user")
                .email("user@example.com")
                .phone(null)
                .role(Role.CUSTOMER)
                .build();

        when(otpService.verifyOtp("user@example.com", "123456")).thenReturn(response);

        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("jwt-token-123"))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("POST /auth/otp/verify with invalid code format (not 6 digits) returns 400 Bad Request")
    void verifyOtp_invalidCodeFormat_returnsBadRequest() throws Exception {
        OtpVerifyRequest request = new OtpVerifyRequest("user@example.com", "123");

        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /auth/otp/verify with wrong/expired code returns 401 Unauthorized")
    void verifyOtp_invalidOrExpiredCode_returns401() throws Exception {
        OtpVerifyRequest request = new OtpVerifyRequest("user@example.com", "999999");
        when(otpService.verifyOtp(eq("user@example.com"), eq("999999")))
                .thenThrow(new InvalidOtpException("Invalid or expired code"));

        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired code"));
    }
}
