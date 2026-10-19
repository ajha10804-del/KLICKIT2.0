package com.klickit.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.config.JwtAuthenticationEntryPoint;
import com.klickit.config.JwtAuthenticationFilter;
import com.klickit.config.JwtService;
import com.klickit.config.SecurityConfig;
import com.klickit.user.controller.UserController;
import com.klickit.user.dto.UpdateProfileRequest;
import com.klickit.user.dto.UserProfileResponse;
import com.klickit.user.entity.Role;
import com.klickit.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("GET /user/profile unauthenticated returns 401")
    void getProfile_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/user/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /user/profile unauthenticated returns 401")
    void updateProfile_unauthenticated_returns401() throws Exception {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .phone("9876543210")
                .build();

        mockMvc.perform(patch("/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@klickit.test", roles = {"CUSTOMER"})
    @DisplayName("GET /user/profile authenticated returns profile")
    void getProfile_authenticated_returnsProfile() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
                .id(UUID.randomUUID())
                .name("Test Customer")
                .email("customer@klickit.test")
                .phone("9876543210")
                .role(Role.CUSTOMER)
                .build();

        when(userService.getProfile("customer@klickit.test")).thenReturn(profile);

        mockMvc.perform(get("/user/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("customer@klickit.test"))
                .andExpect(jsonPath("$.data.phone").value("9876543210"));
    }

    @Test
    @WithMockUser(username = "customer@klickit.test", roles = {"CUSTOMER"})
    @DisplayName("PATCH /user/profile with valid phone updates successfully")
    void updateProfile_validPhone_returnsUpdatedProfile() throws Exception {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .name("Updated Name")
                .phone("+919876543210")
                .build();

        UserProfileResponse updated = UserProfileResponse.builder()
                .id(UUID.randomUUID())
                .name("Updated Name")
                .email("customer@klickit.test")
                .phone("+919876543210")
                .role(Role.CUSTOMER)
                .build();

        when(userService.updateProfile(eq("customer@klickit.test"), any(UpdateProfileRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.phone").value("+919876543210"))
                .andExpect(jsonPath("$.data.name").value("Updated Name"));
    }

    @Test
    @WithMockUser(username = "customer@klickit.test", roles = {"CUSTOMER"})
    @DisplayName("PATCH /user/profile with invalid phone returns 400 Bad Request")
    void updateProfile_invalidPhone_returns400() throws Exception {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .phone("123") // too short, < 7 digits
                .build();

        mockMvc.perform(patch("/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
