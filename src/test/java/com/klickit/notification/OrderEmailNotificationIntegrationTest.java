package com.klickit.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.cart.dto.AddToCartRequest;
import com.klickit.cart.entity.Cart;
import com.klickit.cart.repository.CartRepository;
import com.klickit.notification.client.TransactionalEmailClient;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.entity.Order;
import com.klickit.order.repository.OrderRepository;
import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import com.klickit.user.dto.LoginRequest;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class OrderEmailNotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private TransactionalEmailClient emailClient;

    @Value("${klickit.admin.email}")
    private String adminEmail;

    private User testCustomer;
    private String customerToken;
    private Product testProduct;
    private List<Order> createdOrders = new ArrayList<>();
    private List<Cart> createdCarts = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        Mockito.reset(emailClient);

        // 1. Create and save active product
        testProduct = Product.builder()
                .name("Async Test Noodles " + UUID.randomUUID())
                .description("Instant snack")
                .price(new BigDecimal("30.00"))
                .category("Groceries")
                .active(true)
                .build();
        testProduct = productRepository.save(testProduct);

        // 2. Create and save customer
        String customerEmail = "async_cust_" + UUID.randomUUID() + "@klickit.test";
        testCustomer = User.builder()
                .name("Async Customer")
                .email(customerEmail)
                .phone(UUID.randomUUID().toString().substring(0, 10))
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.CUSTOMER)
                .build();
        testCustomer = userRepository.save(testCustomer);

        // 3. Login to obtain JWT
        LoginRequest loginRequest = new LoginRequest(customerEmail, "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        customerToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    @AfterEach
    void tearDown() {
        for (Order o : createdOrders) {
            try {
                orderRepository.deleteById(o.getId());
            } catch (Exception ignored) {}
        }
        createdOrders.clear();

        for (Cart c : createdCarts) {
            try {
                cartRepository.delete(c);
            } catch (Exception ignored) {}
        }
        createdCarts.clear();

        if (testProduct != null && testProduct.getId() != null) {
            try {
                productRepository.deleteById(testProduct.getId());
            } catch (Exception ignored) {}
        }

        if (testCustomer != null && testCustomer.getId() != null) {
            try {
                userRepository.deleteById(testCustomer.getId());
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("(a) Exactly one sendEmail call asynchronously after committed checkout")
    void committedCheckout_dispatchesEmailOnceAsynchronously() throws Exception {
        when(emailClient.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);

        String sessionId = "sess-" + UUID.randomUUID();

        // Add item to cart
        AddToCartRequest addReq = new AddToCartRequest(sessionId, testProduct.getId(), 2);
        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        // Perform checkout
        CheckoutRequest checkoutReq = new CheckoutRequest(sessionId, "Pooja Customer", "9876543210", "Hostel Room 12", null);
        MvcResult checkoutResult = mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String orderIdStr = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        UUID orderId = UUID.fromString(orderIdStr);
        Order savedOrder = orderRepository.findById(orderId).orElseThrow();
        createdOrders.add(savedOrder);

        // Await async execution of @TransactionalEventListener(AFTER_COMMIT)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(emailClient, times(1)).sendEmail(eq(adminEmail), anyString(), anyString());
            Order updated = orderRepository.findById(orderId).orElseThrow();
            assertThat(updated.isNotificationSent()).isTrue();
        });
    }

    @Test
    @DisplayName("(b) Zero sendEmail calls when checkout fails or rolls back")
    void failedCheckout_doesNotDispatchEmail() throws Exception {
        String emptySessionId = "sess-empty-" + UUID.randomUUID();
        Cart emptyCart = Cart.builder()
                .sessionId(emptySessionId)
                .items(new ArrayList<>())
                .build();
        emptyCart = cartRepository.save(emptyCart);
        createdCarts.add(emptyCart);

        // Attempt checkout on empty cart (fails validation and throws)
        CheckoutRequest checkoutReq = new CheckoutRequest(emptySessionId, "Pooja Customer", "9876543210", "Hostel Room 12", null);
        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot checkout an empty cart"));

        // Verify zero sendEmail calls occurred
        await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(emailClient, never()).sendEmail(anyString(), anyString(), anyString());
        });
    }

    @Test
    @DisplayName("(c) Checkout succeeds and order is saved when emailClient.sendEmail throws exception")
    void checkoutSucceeds_whenMailSenderThrowsException() throws Exception {
        when(emailClient.sendEmail(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Mail provider outage"));

        String sessionId = "sess-" + UUID.randomUUID();

        AddToCartRequest addReq = new AddToCartRequest(sessionId, testProduct.getId(), 1);
        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        CheckoutRequest checkoutReq = new CheckoutRequest(sessionId, "Pooja Customer", "9876543210", "Hostel Room 12", null);
        MvcResult checkoutResult = mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String orderIdStr = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        UUID orderId = UUID.fromString(orderIdStr);
        Order savedOrder = orderRepository.findById(orderId).orElseThrow();
        createdOrders.add(savedOrder);

        assertThat(savedOrder).isNotNull();

        // Await async listener completion: error caught, notificationSent stays false
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(emailClient, atLeastOnce()).sendEmail(anyString(), anyString(), anyString());
            Order updated = orderRepository.findById(orderId).orElseThrow();
            assertThat(updated.isNotificationSent()).isFalse();
        });
    }
}
