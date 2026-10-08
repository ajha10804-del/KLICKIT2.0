package com.klickit.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.config.JwtAuthenticationEntryPoint;
import com.klickit.config.JwtAuthenticationFilter;
import com.klickit.config.JwtService;
import com.klickit.config.SecurityConfig;
import com.klickit.delivery.controller.DeliveryController;
import com.klickit.delivery.dto.DeliveryPartnerResponse;
import com.klickit.delivery.service.DeliveryService;
import com.klickit.order.dto.OrderItemResponse;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.entity.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeliveryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class DeliveryPartnerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeliveryService deliveryService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("Anonymous caller cannot access /delivery/orders (401 Unauthorized)")
    void anonymous_cannotAccessDeliveryOrders() throws Exception {
        mockMvc.perform(get("/delivery/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    @DisplayName("CUSTOMER role cannot access /delivery/orders (403 Forbidden)")
    void customer_cannotAccessDeliveryOrders() throws Exception {
        mockMvc.perform(get("/delivery/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@klickit.com", roles = {"ADMIN"})
    @DisplayName("ADMIN role cannot access /delivery/orders (403 Forbidden)")
    void admin_cannotAccessDeliveryOrders() throws Exception {
        mockMvc.perform(get("/delivery/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "partnerA@klickit.com", roles = {"DELIVERY_PARTNER"})
    @DisplayName("DELIVERY_PARTNER retrieves their assigned orders with items and COD breakdown via JWT identity (200 OK)")
    void deliveryPartner_retrievesOwnOrders() throws Exception {
        UUID orderAId = UUID.randomUUID();
        OrderResponse orderA = OrderResponse.builder()
                .id(orderAId)
                .customerName("Customer A")
                .customerPhone("9876543210")
                .customerAddress("Hostel 1")
                .subtotal(new BigDecimal("95.00"))
                .deliveryFee(new BigDecimal("25.00"))
                .totalAmount(new BigDecimal("120.00"))
                .status(OrderStatus.ASSIGNED)
                .items(List.of(
                        OrderItemResponse.builder()
                                .productName("Maggi")
                                .quantity(2)
                                .price(new BigDecimal("35.00"))
                                .unitPrice(new BigDecimal("35.00"))
                                .lineTotal(new BigDecimal("70.00"))
                                .build(),
                        OrderItemResponse.builder()
                                .productName("Coke")
                                .quantity(1)
                                .price(new BigDecimal("25.00"))
                                .unitPrice(new BigDecimal("25.00"))
                                .lineTotal(new BigDecimal("25.00"))
                                .build()
                ))
                .createdAt(Instant.now())
                .build();

        when(deliveryService.getMyAssignedOrders()).thenReturn(List.of(orderA));

        mockMvc.perform(get("/delivery/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(orderAId.toString()))
                .andExpect(jsonPath("$.data[0].customerName").value("Customer A"))
                .andExpect(jsonPath("$.data[0].subtotal").value(95.00))
                .andExpect(jsonPath("$.data[0].deliveryFee").value(25.00))
                .andExpect(jsonPath("$.data[0].totalAmount").value(120.00))
                .andExpect(jsonPath("$.data[0].items[0].productName").value("Maggi"))
                .andExpect(jsonPath("$.data[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$.data[0].items[0].unitPrice").value(35.00))
                .andExpect(jsonPath("$.data[0].items[0].price").value(35.00))
                .andExpect(jsonPath("$.data[0].items[0].lineTotal").value(70.00))
                .andExpect(jsonPath("$.data[0].items[1].productName").value("Coke"))
                .andExpect(jsonPath("$.data[0].items[1].quantity").value(1))
                .andExpect(jsonPath("$.data[0].items[1].unitPrice").value(25.00))
                .andExpect(jsonPath("$.data[0].items[1].price").value(25.00))
                .andExpect(jsonPath("$.data[0].items[1].lineTotal").value(25.00));
    }

    @Test
    @WithMockUser(username = "partnerA@klickit.com", roles = {"DELIVERY_PARTNER"})
    @DisplayName("Partner A manipulating partnerId path parameter to access Partner B's orders returns 403 Forbidden")
    void partnerA_tamperingPathParameter_returns403() throws Exception {
        UUID partnerBId = UUID.randomUUID();
        when(deliveryService.getAssignedOrders(partnerBId))
                .thenThrow(new AccessDeniedException("You are not authorized to access orders for another delivery partner"));

        mockMvc.perform(get("/delivery/orders/" + partnerBId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @WithMockUser(username = "admin@klickit.com", roles = {"ADMIN"})
    @DisplayName("ADMIN role can list delivery partners for assignment (200 OK)")
    void admin_canListDeliveryPartners() throws Exception {
        DeliveryPartnerResponse partner = DeliveryPartnerResponse.builder()
                .id(UUID.randomUUID())
                .name("Speedy Partner")
                .phone("9998887776")
                .build();

        when(deliveryService.getAllPartners()).thenReturn(List.of(partner));

        mockMvc.perform(get("/delivery/partners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Speedy Partner"));
    }
    @Test
    @DisplayName("Anonymous caller cannot POST /delivery/partners (401 Unauthorized)")
    void anonymous_cannotPostDeliveryPartners() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/delivery/partners")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\",\"email\":\"test@test.com\",\"phone\":\"1234567890\",\"password\":\"123456789012\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    @DisplayName("CUSTOMER role cannot POST /delivery/partners (403 Forbidden)")
    void customer_cannotPostDeliveryPartners() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/delivery/partners")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\",\"email\":\"test@test.com\",\"phone\":\"1234567890\",\"password\":\"123456789012\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "partnerA@klickit.com", roles = {"DELIVERY_PARTNER"})
    @DisplayName("DELIVERY_PARTNER role cannot POST /delivery/partners (403 Forbidden)")
    void deliveryPartner_cannotPostDeliveryPartners() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/delivery/partners")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\",\"email\":\"test@test.com\",\"phone\":\"1234567890\",\"password\":\"123456789012\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@klickit.com", roles = {"ADMIN"})
    @DisplayName("ADMIN role can POST /delivery/partners (201 Created)")
    void admin_canPostDeliveryPartners() throws Exception {
        DeliveryPartnerResponse response = DeliveryPartnerResponse.builder()
                .id(UUID.randomUUID())
                .name("test")
                .phone("1234567890")
                .build();
        when(deliveryService.createPartner(org.mockito.ArgumentMatchers.any())).thenReturn(response);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/delivery/partners")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\",\"email\":\"test@test.com\",\"phone\":\"1234567890\",\"password\":\"123456789012\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "partnerA@klickit.com", roles = {"DELIVERY_PARTNER"})
    @DisplayName("DELIVERY_PARTNER role cannot GET /delivery/partners (403 Forbidden)")
    void deliveryPartner_cannotGetDeliveryPartners() throws Exception {
        mockMvc.perform(get("/delivery/partners"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Anonymous caller cannot PATCH /delivery/orders/{id}/start (401 Unauthorized)")
    void anonymous_cannotStartDelivery() throws Exception {
        mockMvc.perform(patch("/delivery/orders/" + UUID.randomUUID() + "/start"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Anonymous caller cannot PATCH /delivery/orders/{id}/delivered (401 Unauthorized)")
    void anonymous_cannotMarkDelivered() throws Exception {
        mockMvc.perform(patch("/delivery/orders/" + UUID.randomUUID() + "/delivered"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    @DisplayName("CUSTOMER role cannot PATCH /delivery/orders/{id}/start (403 Forbidden)")
    void customer_cannotStartDelivery() throws Exception {
        mockMvc.perform(patch("/delivery/orders/" + UUID.randomUUID() + "/start"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    @DisplayName("CUSTOMER role cannot PATCH /delivery/orders/{id}/delivered (403 Forbidden)")
    void customer_cannotMarkDelivered() throws Exception {
        mockMvc.perform(patch("/delivery/orders/" + UUID.randomUUID() + "/delivered"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "partnerA@klickit.com", roles = {"DELIVERY_PARTNER"})
    @DisplayName("DELIVERY_PARTNER can start delivery (200 OK)")
    void deliveryPartner_canStartDelivery() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse response = OrderResponse.builder()
                .id(orderId)
                .customerName("Customer A")
                .customerPhone("9876543210")
                .customerAddress("Hostel 1")
                .totalAmount(new BigDecimal("120.00"))
                .status(OrderStatus.OUT_FOR_DELIVERY)
                .items(List.of())
                .createdAt(Instant.now())
                .build();

        when(deliveryService.startDelivery(orderId)).thenReturn(response);

        mockMvc.perform(patch("/delivery/orders/" + orderId + "/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("OUT_FOR_DELIVERY"));
    }

    @Test
    @WithMockUser(username = "partnerA@klickit.com", roles = {"DELIVERY_PARTNER"})
    @DisplayName("DELIVERY_PARTNER can complete delivery (200 OK)")
    void deliveryPartner_canMarkDelivered() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse response = OrderResponse.builder()
                .id(orderId)
                .customerName("Customer A")
                .customerPhone("9876543210")
                .customerAddress("Hostel 1")
                .totalAmount(new BigDecimal("120.00"))
                .status(OrderStatus.DELIVERED)
                .items(List.of())
                .createdAt(Instant.now())
                .build();

        when(deliveryService.markDelivered(orderId)).thenReturn(response);

        mockMvc.perform(patch("/delivery/orders/" + orderId + "/delivered"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
    }

    @Test
    @WithMockUser(username = "partnerA@klickit.com", roles = {"DELIVERY_PARTNER"})
    @DisplayName("Starting delivery with invalid state returns 400 Bad Request")
    void startDelivery_invalidState_returns400() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(deliveryService.startDelivery(orderId))
                .thenThrow(new IllegalStateException("Cannot transition order from PLACED to OUT_FOR_DELIVERY"));

        mockMvc.perform(patch("/delivery/orders/" + orderId + "/start"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cannot transition order from PLACED to OUT_FOR_DELIVERY"));
    }

    @Test
    @WithMockUser(username = "partnerA@klickit.com", roles = {"DELIVERY_PARTNER"})
    @DisplayName("Completing delivery with invalid state returns 400 Bad Request")
    void markDelivered_invalidState_returns400() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(deliveryService.markDelivered(orderId))
                .thenThrow(new IllegalStateException("Cannot transition order from ASSIGNED to DELIVERED"));

        mockMvc.perform(patch("/delivery/orders/" + orderId + "/delivered"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cannot transition order from ASSIGNED to DELIVERED"));
    }

    @Test
    @WithMockUser(username = "partnerB@klickit.com", roles = {"DELIVERY_PARTNER"})
    @DisplayName("Wrong delivery partner starting delivery returns 403 Forbidden")
    void wrongDriver_startDelivery_returns403() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(deliveryService.startDelivery(orderId))
                .thenThrow(new AccessDeniedException("You are not authorized to deliver this order"));

        mockMvc.perform(patch("/delivery/orders/" + orderId + "/start"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @WithMockUser(username = "partnerB@klickit.com", roles = {"DELIVERY_PARTNER"})
    @DisplayName("Wrong delivery partner completing delivery returns 403 Forbidden")
    void wrongDriver_markDelivered_returns403() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(deliveryService.markDelivered(orderId))
                .thenThrow(new AccessDeniedException("You are not authorized to deliver this order"));

        mockMvc.perform(patch("/delivery/orders/" + orderId + "/delivered"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }
}
