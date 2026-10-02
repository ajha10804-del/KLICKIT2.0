package com.klickit.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.config.JwtAuthenticationEntryPoint;
import com.klickit.config.JwtAuthenticationFilter;
import com.klickit.config.JwtService;
import com.klickit.config.SecurityConfig;
import com.klickit.order.controller.AdminOrderController;
import com.klickit.order.controller.OrderController;
import com.klickit.order.dto.AssignDeliveryRequest;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.dto.UpdateOrderStatusRequest;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminOrderController.class, OrderController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class OrderAuthorizationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("Anonymous user accessing protected admin orders endpoint returns 401 Unauthorized")
    void anonymousUser_adminOrders_returns401() throws Exception {
        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    @DisplayName("CUSTOMER accessing admin orders endpoint returns 403 Forbidden")
    void customerUser_adminOrders_returns403() throws Exception {
        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@klickit.com", roles = {"ADMIN"})
    @DisplayName("ADMIN accessing admin orders endpoint returns 200 OK")
    void adminUser_adminOrders_returns200() throws Exception {
        OrderResponse order = createSampleOrder(UUID.randomUUID(), "customerA@test.com");
        when(orderService.getAllOrders()).thenReturn(List.of(order));

        mockMvc.perform(get("/admin/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].customerName").value("Sample Customer"));
    }

    @Test
    @WithMockUser(username = "customerA@test.com", roles = {"CUSTOMER"})
    @DisplayName("Customer A attempting to access Customer B's order returns 403 Forbidden")
    void customerA_accessingCustomerBOrder_returns403() throws Exception {
        UUID orderBId = UUID.randomUUID();
        when(orderService.getOrderById(orderBId))
                .thenThrow(new AccessDeniedException("You are not authorized to access this order"));

        mockMvc.perform(get("/orders/" + orderBId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @WithMockUser(username = "customerA@test.com", roles = {"CUSTOMER"})
    @DisplayName("Customer A accessing their own order returns 200 OK")
    void customerA_accessingOwnOrder_returns200() throws Exception {
        UUID orderAId = UUID.randomUUID();
        OrderResponse orderA = createSampleOrder(orderAId, "customerA@test.com");
        when(orderService.getOrderById(orderAId)).thenReturn(orderA);

        mockMvc.perform(get("/orders/" + orderAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(orderAId.toString()))
                .andExpect(jsonPath("$.data.customerEmail").value("customerA@test.com"));
    }

    @Test
    @WithMockUser(username = "admin@klickit.com", roles = {"ADMIN"})
    @DisplayName("ADMIN accessing Customer B's order returns 200 OK (universal access)")
    void admin_accessingAnyCustomerOrder_returns200() throws Exception {
        UUID orderBId = UUID.randomUUID();
        OrderResponse orderB = createSampleOrder(orderBId, "customerB@test.com");
        when(orderService.getOrderById(orderBId)).thenReturn(orderB);

        mockMvc.perform(get("/orders/" + orderBId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(orderBId.toString()))
                .andExpect(jsonPath("$.data.customerEmail").value("customerB@test.com"));
    }

    @Test
    @WithMockUser(username = "admin@klickit.com", roles = {"ADMIN"})
    @DisplayName("Admin updating order status with illegal transition returns 400 Bad Request")
    void admin_illegalStatusTransition_returns400() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.updateStatus(eq(orderId), any(UpdateOrderStatusRequest.class)))
                .thenThrow(new IllegalStateException("Cannot transition order from ASSIGNED to DELIVERED"));

        mockMvc.perform(patch("/admin/orders/" + orderId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateOrderStatusRequest(OrderStatus.DELIVERED))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cannot transition order from ASSIGNED to DELIVERED"));
    }

    @Test
    @WithMockUser(username = "admin@klickit.com", roles = {"ADMIN"})
    @DisplayName("Admin assigning delivery partner to delivered order returns 400 Bad Request")
    void admin_assignToDeliveredOrder_returns400() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        when(orderService.assignDeliveryPartner(eq(orderId), any(AssignDeliveryRequest.class)))
                .thenThrow(new IllegalStateException("Cannot assign delivery to a delivered order"));

        mockMvc.perform(patch("/admin/orders/" + orderId + "/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AssignDeliveryRequest(partnerId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cannot assign delivery to a delivered order"));
    }

    @Test
    @DisplayName("Anonymous user calling checkout returns 401 Unauthorized")
    void anonymousUser_checkout_returns401() throws Exception {
        CheckoutRequest request = new CheckoutRequest("sess-1", "Sample Customer", "9876543210", "Room 101, Hostel 1", null);
        mockMvc.perform(post("/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Expired JWT token calling checkout returns 401 Unauthorized")
    void expiredToken_checkout_returns401() throws Exception {
        when(jwtService.extractUsername("expired.token"))
                .thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "Token has expired"));

        CheckoutRequest request = new CheckoutRequest("sess-1", "Sample Customer", "9876543210", "Room 101, Hostel 1", null);
        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer expired.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "admin@klickit.com", roles = {"ADMIN"})
    @DisplayName("ADMIN user calling checkout returns 403 Forbidden")
    void adminUser_checkout_returns403() throws Exception {
        CheckoutRequest request = new CheckoutRequest("sess-1", "Sample Customer", "9876543210", "Room 101, Hostel 1", null);
        mockMvc.perform(post("/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "driver@klickit.com", roles = {"DELIVERY_PARTNER"})
    @DisplayName("DELIVERY_PARTNER user calling checkout returns 403 Forbidden")
    void deliveryPartnerUser_checkout_returns403() throws Exception {
        CheckoutRequest request = new CheckoutRequest("sess-1", "Sample Customer", "9876543210", "Room 101, Hostel 1", null);
        mockMvc.perform(post("/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer@klickit.com", roles = {"CUSTOMER"})
    @DisplayName("CUSTOMER user calling checkout returns 201 Created and order has customerEmail")
    void customerUser_checkout_returns201() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse order = createSampleOrder(orderId, "customer@klickit.com");
        when(orderService.checkout(any(CheckoutRequest.class))).thenReturn(order);

        CheckoutRequest request = new CheckoutRequest("sess-1", "Sample Customer", "9876543210", "Room 101, Hostel 1", null);
        mockMvc.perform(post("/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(orderId.toString()))
                .andExpect(jsonPath("$.data.customerEmail").value("customer@klickit.com"));
    }

    @Test
    @WithMockUser(username = "customerA@klickit.com", roles = {"CUSTOMER"})
    @DisplayName("Request-body customerEmail cannot override authenticated principal email")
    void requestBodyEmailCannotOverridePrincipal_returnsPrincipalEmail() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse order = createSampleOrder(orderId, "customerA@klickit.com");
        when(orderService.checkout(any(CheckoutRequest.class))).thenReturn(order);

        CheckoutRequest request = new CheckoutRequest("sess-1", "Customer A", "9876543210", "Room 101, Hostel 1", null, "customerB@attacker.com");
        mockMvc.perform(post("/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customerEmail").value("customerA@klickit.com"));
    }

    private OrderResponse createSampleOrder(UUID id, String customerEmail) {
        return OrderResponse.builder()
                .id(id)
                .customerName("Sample Customer")
                .customerPhone("9876543210")
                .customerAddress("Room 101, Hostel 1")
                .customerEmail(customerEmail)
                .deadline(Instant.now().plusSeconds(1800))
                .totalAmount(new BigDecimal("150.00"))
                .status(OrderStatus.PLACED)
                .items(List.of())
                .createdAt(Instant.now())
                .build();
    }
}
