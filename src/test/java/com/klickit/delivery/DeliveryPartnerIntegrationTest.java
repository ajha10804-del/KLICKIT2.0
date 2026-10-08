package com.klickit.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.delivery.dto.CreateDeliveryPartnerRequest;
import com.klickit.delivery.entity.DeliveryPartner;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderItem;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.repository.OrderRepository;
import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private List<User> createdUsers = new ArrayList<>();
    private List<DeliveryPartner> createdPartners = new ArrayList<>();
    private List<Order> createdOrders = new ArrayList<>();
    private List<Product> createdProducts = new ArrayList<>();
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
        for (Order order : createdOrders) {
            orderRepository.delete(order);
        }
        createdOrders.clear();

        for (Product product : createdProducts) {
            productRepository.delete(product);
        }
        createdProducts.clear();

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

    private String loginAndGetToken(String email, String password) throws Exception {
        String loginPayload = String.format("{\"email\":\"%s\", \"password\":\"%s\"}", email, password);
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    private DeliveryPartner createDriver(String name, String email, String phone, String password) throws Exception {
        CreateDeliveryPartnerRequest request = new CreateDeliveryPartnerRequest(name, phone, email, password);
        mockMvc.perform(post("/delivery/partners")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        trackCreatedEntities(email);
        return deliveryPartnerRepository.findByUserEmail(email).orElseThrow();
    }

    @Test
    @DisplayName("Driver orders include items, unitPrice, subtotal, and COD breakdown; snapshot prices remain unchanged when catalog updates")
    void driverOrdersIncludeLineItemsAndCodBreakdown_withSnapshotIntegrity() throws Exception {
        String driverEmail = "driver_items_" + UUID.randomUUID() + "@klickit.com";
        String driverPhone = UUID.randomUUID().toString().substring(0, 10);
        DeliveryPartner partner = createDriver("Driver Items", driverEmail, driverPhone, "driver_pass_123");
        String driverToken = loginAndGetToken(driverEmail, "driver_pass_123");

        Product product = Product.builder()
                .name("Snapshot Test Product")
                .description("Test description")
                .price(new BigDecimal("50.00"))
                .category("Groceries")
                .active(true)
                .build();
        Product savedProduct = productRepository.save(product);
        createdProducts.add(savedProduct);

        Order order = Order.builder()
                .customerName("Alice Customer")
                .customerPhone("9876500001")
                .customerAddress("Block C, Room 204")
                .customerEmail("alice@test.com")
                .deadline(Instant.now().plusSeconds(900))
                .deliveryFee(new BigDecimal("25.00"))
                .totalAmount(new BigDecimal("155.00"))
                .deliveryPartnerId(partner.getId())
                .deliveryPartnerName(partner.getName())
                .deliveryPartnerPhone(partner.getPhone())
                .status(OrderStatus.ASSIGNED)
                .build();

        OrderItem item1 = OrderItem.builder()
                .order(order)
                .productName("Snapshot Test Product")
                .quantity(2)
                .price(new BigDecimal("50.00"))
                .build();

        OrderItem item2 = OrderItem.builder()
                .order(order)
                .productName("Second Item")
                .quantity(1)
                .price(new BigDecimal("30.00"))
                .build();

        order.setItems(new ArrayList<>(List.of(item1, item2)));
        Order savedOrder = orderRepository.save(order);
        createdOrders.add(savedOrder);

        // Update catalog product price in the products table to 999.00
        savedProduct.setPrice(new BigDecimal("999.00"));
        productRepository.save(savedProduct);

        // Driver fetches assigned orders
        mockMvc.perform(get("/delivery/orders")
                .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(savedOrder.getId().toString()))
                .andExpect(jsonPath("$.data[0].customerName").value("Alice Customer"))
                .andExpect(jsonPath("$.data[0].customerPhone").value("9876500001"))
                .andExpect(jsonPath("$.data[0].customerAddress").value("Block C, Room 204"))
                .andExpect(jsonPath("$.data[0].subtotal").value(130.00))
                .andExpect(jsonPath("$.data[0].deliveryFee").value(25.00))
                .andExpect(jsonPath("$.data[0].totalAmount").value(155.00))
                .andExpect(jsonPath("$.data[0].items", hasSize(2)))
                .andExpect(jsonPath("$.data[0].items[0].productName").value("Snapshot Test Product"))
                .andExpect(jsonPath("$.data[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$.data[0].items[0].unitPrice").value(50.00))
                .andExpect(jsonPath("$.data[0].items[0].price").value(50.00))
                .andExpect(jsonPath("$.data[0].items[0].lineTotal").value(100.00))
                .andExpect(jsonPath("$.data[0].items[1].productName").value("Second Item"))
                .andExpect(jsonPath("$.data[0].items[1].quantity").value(1))
                .andExpect(jsonPath("$.data[0].items[1].unitPrice").value(30.00))
                .andExpect(jsonPath("$.data[0].items[1].price").value(30.00))
                .andExpect(jsonPath("$.data[0].items[1].lineTotal").value(30.00));
    }

    @Test
    @DisplayName("Driver isolation: Driver B cannot view Driver A's assigned order")
    void driverIsolation_driverCannotViewAnotherDriversOrders() throws Exception {
        String driverEmailA = "driver_a_" + UUID.randomUUID() + "@klickit.com";
        String driverPhoneA = UUID.randomUUID().toString().substring(0, 10);
        DeliveryPartner partnerA = createDriver("Driver A", driverEmailA, driverPhoneA, "driver_password_a_123");
        String tokenA = loginAndGetToken(driverEmailA, "driver_password_a_123");

        String driverEmailB = "driver_b_" + UUID.randomUUID() + "@klickit.com";
        String driverPhoneB = UUID.randomUUID().toString().substring(0, 10);
        DeliveryPartner partnerB = createDriver("Driver B", driverEmailB, driverPhoneB, "driver_password_b_123");
        String tokenB = loginAndGetToken(driverEmailB, "driver_password_b_123");

        Order orderA = Order.builder()
                .customerName("Customer for A")
                .customerPhone("9876500002")
                .customerAddress("Hostel 1")
                .customerEmail("ca@test.com")
                .deadline(Instant.now().plusSeconds(900))
                .deliveryFee(new BigDecimal("25.00"))
                .totalAmount(new BigDecimal("100.00"))
                .deliveryPartnerId(partnerA.getId())
                .deliveryPartnerName(partnerA.getName())
                .deliveryPartnerPhone(partnerA.getPhone())
                .status(OrderStatus.ASSIGNED)
                .build();
        Order savedOrderA = orderRepository.save(orderA);
        createdOrders.add(savedOrderA);

        // Driver A sees Order A
        mockMvc.perform(get("/delivery/orders")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(savedOrderA.getId().toString()));

        // Driver B does NOT see Order A
        mockMvc.perform(get("/delivery/orders")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("Role barrier: CUSTOMER and ADMIN tokens rejected with 403 Forbidden on /delivery/orders")
    void roleBarrier_customerAndAdminRejectedFromDeliveryOrders() throws Exception {
        // Admin token cannot access /delivery/orders
        mockMvc.perform(get("/delivery/orders")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        // Customer user cannot access /delivery/orders
        String customerEmail = "cust_" + UUID.randomUUID() + "@klickit.com";
        User customer = User.builder()
                .name("Regular Customer")
                .email(customerEmail)
                .phone(UUID.randomUUID().toString().substring(0, 10))
                .password(passwordEncoder.encode("customer_password_123"))
                .role(Role.CUSTOMER)
                .build();
        User savedCustomer = userRepository.save(customer);
        createdUsers.add(savedCustomer);

        String customerToken = loginAndGetToken(customerEmail, "customer_password_123");

        mockMvc.perform(get("/delivery/orders")
                .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }
}
