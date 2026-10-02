package com.klickit.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.delivery.dto.CreateDeliveryPartnerRequest;
import com.klickit.delivery.entity.DeliveryPartner;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DeliveryPartnerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private List<User> createdUsers = new ArrayList<>();
    private List<DeliveryPartner> createdPartners = new ArrayList<>();
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        String uniqueAdmin = "admin_" + UUID.randomUUID() + "@klickit.com";
        User adminUser = User.builder()
                .name("Test Admin")
                .email(uniqueAdmin)
                .phone(UUID.randomUUID().toString().substring(0, 10))
                .password(passwordEncoder.encode("admin_password"))
                .role(Role.ADMIN)
                .build();
        User savedAdmin = userRepository.save(adminUser);
        createdUsers.add(savedAdmin);

        String loginPayload = String.format("{\"email\":\"%s\", \"password\":\"admin_password\"}", uniqueAdmin);
        MvcResult adminLoginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = objectMapper.readTree(adminLoginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    @AfterEach
    void tearDown() {
        for (DeliveryPartner partner : createdPartners) {
            deliveryPartnerRepository.delete(partner);
        }
        createdPartners.clear();

        for (User user : createdUsers) {
            userRepository.delete(user);
        }
        createdUsers.clear();
    }

    private void trackCreatedEntities(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            deliveryPartnerRepository.findByUser(user).ifPresent(partner -> createdPartners.add(partner));
            createdUsers.add(user);
        });
    }

    @Test
    @DisplayName("Creates User and linked DeliveryPartner")
    void createsUserAndLinkedPartner() throws Exception {
        String driverEmail = "driver_" + UUID.randomUUID() + "@klickit.com";
        String driverPhone = UUID.randomUUID().toString().substring(0, 10);
        CreateDeliveryPartnerRequest request = new CreateDeliveryPartnerRequest(
                "Test Driver", driverPhone, driverEmail, "driver_password_123"
        );

        long initialUserCount = userRepository.count();
        long initialPartnerCount = deliveryPartnerRepository.count();

        mockMvc.perform(post("/delivery/partners")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        trackCreatedEntities(driverEmail);

        assertThat(userRepository.count()).isEqualTo(initialUserCount + 1);
        assertThat(deliveryPartnerRepository.count()).isEqualTo(initialPartnerCount + 1);

        User createdUser = userRepository.findByEmail(driverEmail).orElseThrow();
        assertThat(createdUser.getRole()).isEqualTo(Role.DELIVERY_PARTNER);

        DeliveryPartner createdPartner = deliveryPartnerRepository.findByUser(createdUser).orElseThrow();
        assertThat(createdPartner.getPhone()).isEqualTo(driverPhone);
        assertThat(createdPartner.getName()).isEqualTo("Test Driver");
    }

    @Test
    @DisplayName("Stores BCrypt password, not plaintext")
    void storesBcryptPasswordNotPlaintext() throws Exception {
        String driverEmail = "driver_" + UUID.randomUUID() + "@klickit.com";
        String driverPhone = UUID.randomUUID().toString().substring(0, 10);
        String plaintextPassword = "driver_password_123";
        CreateDeliveryPartnerRequest request = new CreateDeliveryPartnerRequest(
                "Test Driver", driverPhone, driverEmail, plaintextPassword
        );

        mockMvc.perform(post("/delivery/partners")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        trackCreatedEntities(driverEmail);

        User createdUser = userRepository.findByEmail(driverEmail).orElseThrow();
        assertThat(createdUser.getPassword()).isNotEqualTo(plaintextPassword);
        assertThat(passwordEncoder.matches(plaintextPassword, createdUser.getPassword())).isTrue();
    }

    @Test
    @DisplayName("Rejects duplicate email with zero new rows")
    void rejectsDuplicateEmail() throws Exception {
        String driverEmail = "driver_" + UUID.randomUUID() + "@klickit.com";
        String driverPhone = UUID.randomUUID().toString().substring(0, 10);
        CreateDeliveryPartnerRequest request = new CreateDeliveryPartnerRequest(
                "Test Driver", driverPhone, driverEmail, "driver_password_123"
        );

        mockMvc.perform(post("/delivery/partners")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        trackCreatedEntities(driverEmail);

        long currentUserCount = userRepository.count();
        long currentPartnerCount = deliveryPartnerRepository.count();

        String newPhone = UUID.randomUUID().toString().substring(0, 10);
        CreateDeliveryPartnerRequest duplicateRequest = new CreateDeliveryPartnerRequest(
                "Another Driver", newPhone, driverEmail, "password123456"
        );

        mockMvc.perform(post("/delivery/partners")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.count()).isEqualTo(currentUserCount);
        assertThat(deliveryPartnerRepository.count()).isEqualTo(currentPartnerCount);
    }

    @Test
    @DisplayName("Rejects duplicate user phone with zero new rows")
    void rejectsDuplicateUserPhone() throws Exception {
        String driverEmail = "driver_" + UUID.randomUUID() + "@klickit.com";
        String driverPhone = UUID.randomUUID().toString().substring(0, 10);
        CreateDeliveryPartnerRequest request = new CreateDeliveryPartnerRequest(
                "Test Driver", driverPhone, driverEmail, "driver_password_123"
        );

        mockMvc.perform(post("/delivery/partners")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        trackCreatedEntities(driverEmail);

        long currentUserCount = userRepository.count();
        long currentPartnerCount = deliveryPartnerRepository.count();

        String newEmail = "driver_" + UUID.randomUUID() + "@klickit.com";
        CreateDeliveryPartnerRequest duplicateRequest = new CreateDeliveryPartnerRequest(
                "Another Driver", driverPhone, newEmail, "password123456"
        );

        mockMvc.perform(post("/delivery/partners")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.count()).isEqualTo(currentUserCount);
        assertThat(deliveryPartnerRepository.count()).isEqualTo(currentPartnerCount);
    }

    @Test
    @DisplayName("Rejects duplicate partner phone with zero new rows")
    void rejectsDuplicatePartnerPhone() throws Exception {
        String driverEmail = "driver_" + UUID.randomUUID() + "@klickit.com";
        String driverPhone = UUID.randomUUID().toString().substring(0, 10);
        CreateDeliveryPartnerRequest request = new CreateDeliveryPartnerRequest(
                "Test Driver", driverPhone, driverEmail, "driver_password_123"
        );

        mockMvc.perform(post("/delivery/partners")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        trackCreatedEntities(driverEmail);

        long currentUserCount = userRepository.count();
        long currentPartnerCount = deliveryPartnerRepository.count();

        String newEmail = "driver_" + UUID.randomUUID() + "@klickit.com";
        // Attempting to create with the same partner phone
        CreateDeliveryPartnerRequest duplicateRequest = new CreateDeliveryPartnerRequest(
                "Another Driver", driverPhone, newEmail, "password123456"
        );

        mockMvc.perform(post("/delivery/partners")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.count()).isEqualTo(currentUserCount);
        assertThat(deliveryPartnerRepository.count()).isEqualTo(currentPartnerCount);
    }

    @Test
    @DisplayName("Provisioned driver can login and list orders")
    void provisionedDriverCanLoginAndListOrders() throws Exception {
        String driverEmail = "driver_" + UUID.randomUUID() + "@klickit.com";
        String driverPhone = UUID.randomUUID().toString().substring(0, 10);
        String plaintextPassword = "driver_password_123";
        CreateDeliveryPartnerRequest request = new CreateDeliveryPartnerRequest(
                "Test Driver", driverPhone, driverEmail, plaintextPassword
        );

        mockMvc.perform(post("/delivery/partners")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        trackCreatedEntities(driverEmail);

        String driverLoginPayload = String.format("{\"email\":\"%s\", \"password\":\"%s\"}", driverEmail, plaintextPassword);
        MvcResult driverLoginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(driverLoginPayload))
                .andExpect(status().isOk())
                .andReturn();
        String driverToken = objectMapper.readTree(driverLoginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        mockMvc.perform(get("/delivery/orders")
                .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());
    }
}
