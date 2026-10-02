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
    @DisplayName("POST /auth/register without role in JSON payload succeeds with role CUSTOMER")
    void register_withoutRoleInJson_succeedsAsCustomer() throws Exception {
        AuthResponse response = AuthResponse.builder()
                .token("jwt-token")
                .userId(UUID.randomUUID())
                .name("Alice")
                .email("alice@test.com")
                .role(Role.CUSTOMER)
                .build();

        when(userService.register(any(RegisterRequest.class))).thenReturn(response);

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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("POST /auth/register with role=ADMIN in JSON payload returns role CUSTOMER")
    void register_withRoleAdminInJson_returnsRoleCustomer() throws Exception {
        AuthResponse response = AuthResponse.builder()
                .token("jwt-token")
                .userId(UUID.randomUUID())
                .name("Jay")
                .email("jay@test.com")
                .role(Role.CUSTOMER)
                .build();

        when(userService.register(any(RegisterRequest.class))).thenReturn(response);

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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));
    }
}
