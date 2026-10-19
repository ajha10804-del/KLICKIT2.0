package com.klickit.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.config.JwtAuthenticationEntryPoint;
import com.klickit.config.JwtAuthenticationFilter;
import com.klickit.config.JwtService;
import com.klickit.config.SecurityConfig;
import com.klickit.user.controller.AuthController;
import com.klickit.user.dto.AuthResponse;
import com.klickit.user.dto.RegisterRequest;
import com.klickit.user.entity.Role;
import com.klickit.user.service.UserService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class AuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /auth/register rejects direct registration without OTP verification (400 Bad Request)")
    void register_directUnverified_isRejected() throws Exception {
        String json = """
                {
                    "name": "Alice",
                    "email": "alice@test.com",
                    "phone": "1111111111",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Direct registration without email verification is disabled. Please verify your email via /auth/register/request-otp."));
    }

    @Test
    @DisplayName("POST /auth/register with role=ADMIN is rejected and cannot bypass email verification")
    void register_withRoleAdminInJson_isRejected() throws Exception {
        String json = """
                {
                    "name": "Jay",
                    "email": "jay@test.com",
                    "phone": "2222222222",
                    "password": "password123",
                    "role": "ADMIN"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Direct registration without email verification is disabled. Please verify your email via /auth/register/request-otp."));
    }

    @Test
    @DisplayName("POST /auth/register/request-otp succeeds with 200 OK")
    void requestRegistrationOtp_succeeds() throws Exception {
        String json = """
                {
                    "name": "Sarah",
                    "email": "sarah@test.com",
                    "phone": "9876543210",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/auth/register/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Verification code sent to your email"));

        verify(userService).requestRegistrationOtp(any());
    }

    @Test
    @DisplayName("POST /auth/register/verify-otp succeeds with 201 CREATED and CUSTOMER role")
    void verifyRegistrationOtp_succeeds() throws Exception {
        AuthResponse response = AuthResponse.builder()
                .token("jwt-token-verified")
                .userId(UUID.randomUUID())
                .name("Sarah")
                .email("sarah@test.com")
                .role(Role.CUSTOMER)
                .build();

        when(userService.verifyRegistrationOtp(any())).thenReturn(response);

        String json = """
                {
                    "name": "Sarah",
                    "email": "sarah@test.com",
                    "phone": "9876543210",
                    "password": "password123",
                    "code": "123456",
                    "role": "ADMIN"
                }
                """;

        mockMvc.perform(post("/auth/register/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.token").value("jwt-token-verified"));
    }
}
