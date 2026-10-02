package com.klickit.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.cart.dto.AddToCartRequest;
import com.klickit.cart.repository.CartRepository;
import com.klickit.delivery.entity.DeliveryPartner;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.entity.Order;
import com.klickit.order.repository.OrderRepository;
import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class CustomerOrderOwnershipIntegrationTest {

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
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${klickit.jwt.secret}")
    private String secretKey;

    private List<User> testUsers = new ArrayList<>();
    private List<Order> testOrders = new ArrayList<>();
    private List<DeliveryPartner> testPartners = new ArrayList<>();
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = productRepository.save(Product.builder()
                .name("Integration Test Apples")
                .description("Fresh juicy apples")
                .price(new BigDecimal("50.00"))
                .category("Fruits")
                .active(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        for (Order order : testOrders) {
            orderRepository.delete(order);
        }
        testOrders.clear();

        for (DeliveryPartner partner : testPartners) {
            deliveryPartnerRepository.delete(partner);
        }
        testPartners.clear();

        for (User user : testUsers) {
            userRepository.delete(user);
        }
        testUsers.clear();

        if (testProduct != null && testProduct.getId() != null) {
            productRepository.delete(testProduct);
        }
    }

    private String createAndLoginUser(String name, String email, String phone, String password, Role role) throws Exception {
        User user = User.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(password))
                .role(role)
                .build();
        User saved = userRepository.save(user);
        testUsers.add(saved);

        if (role == Role.DELIVERY_PARTNER) {
            DeliveryPartner partner = DeliveryPartner.builder()
                    .user(saved)
                    .name(name)
                    .phone(phone)
                    .build();
            testPartners.add(deliveryPartnerRepository.save(partner));
        }

        String loginJson = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    private String generateExpiredToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date(System.currentTimeMillis() - 100000))
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey)))
                .compact();
    }

    @Test
    @DisplayName("Public cart endpoints remain accessible without authentication")
    void publicCart_remainsAccessibleWithoutAuth() throws Exception {
        String sessionId = "sess_public_" + UUID.randomUUID();
        AddToCartRequest addReq = new AddToCartRequest(sessionId, testProduct.getId(), 2);

        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId));
    }

    @Test
    @DisplayName("POST /orders/checkout without token returns 401 Unauthorized")
    void anonymous_checkout_returns401() throws Exception {
        String sessionId = "sess_" + UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest(sessionId, "Customer", "9876543210", "123 Street 10 chars", null);

        mockMvc.perform(post("/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /orders/checkout with expired JWT returns 401 Unauthorized")
    void expiredToken_checkout_returns401() throws Exception {
        String sessionId = "sess_" + UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest(sessionId, "Customer", "9876543210", "123 Street 10 chars", null);
        String expiredToken = generateExpiredToken("customer@example.com");

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + expiredToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /orders/checkout with ADMIN token returns 403 Forbidden")
    void adminToken_checkout_returns403() throws Exception {
        String adminEmail = "admin_" + UUID.randomUUID() + "@klickit.com";
        String adminToken = createAndLoginUser("Admin User", adminEmail, "9811198111", "adminpass", Role.ADMIN);

        String sessionId = "sess_" + UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest(sessionId, "Admin As Customer", "9811198111", "123 Street 10 chars", null);

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /orders/checkout with DELIVERY_PARTNER token returns 403 Forbidden")
    void driverToken_checkout_returns403() throws Exception {
        String driverEmail = "driver_" + UUID.randomUUID() + "@klickit.com";
        String driverToken = createAndLoginUser("Driver User", driverEmail, "9822298222", "driverpass", Role.DELIVERY_PARTNER);

        String sessionId = "sess_" + UUID.randomUUID();
        CheckoutRequest request = new CheckoutRequest(sessionId, "Driver As Customer", "9822298222", "123 Street 10 chars", null);

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Complete customer ownership flow: checkout, list orders, track order, and data isolation")
    void fullCustomerOwnershipFlow_checkout_getOrder_getMyOrders_customerIsolation() throws Exception {
        // Setup Customer A
        String emailA = "customer_a_" + UUID.randomUUID() + "@klickit.com";
        String tokenA = createAndLoginUser("Customer A", emailA, "9833398333", "passA123", Role.CUSTOMER);

        // Setup Customer B
        String emailB = "customer_b_" + UUID.randomUUID() + "@klickit.com";
        String tokenB = createAndLoginUser("Customer B", emailB, "9844498444", "passB123", Role.CUSTOMER);

        // 1. Add item to cart for Customer A
        String sessionIdA = "cart_sess_" + UUID.randomUUID();
        AddToCartRequest addReq = new AddToCartRequest(sessionIdA, testProduct.getId(), 3);
        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        // 2. Customer A checks out -> 201 Created
        CheckoutRequest checkoutReq = new CheckoutRequest(
                sessionIdA, "Customer A", "9833398333", "Flat 4, Vijay Nagar, Indore", null
        );

        MvcResult checkoutResult = mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customerEmail").value(emailA))
                .andReturn();

        String orderIdStr = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        UUID orderIdA = UUID.fromString(orderIdStr);

        orderRepository.findById(orderIdA).ifPresent(testOrders::add);

        // 3. Customer A calls GET /orders -> order is visible in list
        mockMvc.perform(get("/orders")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(orderIdStr))
                .andExpect(jsonPath("$.data[0].customerEmail").value(emailA));

        // 4. Customer A calls GET /orders/{id} -> order details are accessible (trackable)
        mockMvc.perform(get("/orders/" + orderIdA)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(orderIdStr))
                .andExpect(jsonPath("$.data.customerEmail").value(emailA));

        // 5. Customer B calls GET /orders -> Customer A's order is NOT visible
        mockMvc.perform(get("/orders")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        // 6. Customer B calls GET /orders/{orderIdA} -> returns 403 Forbidden
        mockMvc.perform(get("/orders/" + orderIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Request-body customerEmail cannot override authenticated principal email")
    void requestBodyEmail_cannotOverrideAuthenticatedPrincipal() throws Exception {
        String emailA = "customer_a_" + UUID.randomUUID() + "@klickit.com";
        String tokenA = createAndLoginUser("Customer A", emailA, "9855598555", "passA123", Role.CUSTOMER);

        String sessionId = "cart_sess_" + UUID.randomUUID();
        AddToCartRequest addReq = new AddToCartRequest(sessionId, testProduct.getId(), 1);
        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        // Malicious client sends customerEmail = attacker@evil.com in request body
        CheckoutRequest checkoutReq = new CheckoutRequest(
                sessionId, "Customer A", "9855598555", "Flat 4, Vijay Nagar, Indore", null, "attacker@evil.com"
        );

        MvcResult checkoutResult = mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customerEmail").value(emailA))
                .andReturn();

        String orderIdStr = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
        UUID orderId = UUID.fromString(orderIdStr);

        Order savedOrder = orderRepository.findById(orderId).orElseThrow();
        testOrders.add(savedOrder);

        assertThat(savedOrder.getCustomerEmail()).isEqualTo(emailA);
        assertThat(savedOrder.getCustomerEmail()).isNotEqualTo("attacker@evil.com");
    }
}
