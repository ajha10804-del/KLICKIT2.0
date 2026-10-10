package com.klickit.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.cart.dto.AddToCartRequest;
import com.klickit.cart.repository.CartRepository;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.repository.OrderRepository;
import com.klickit.order.service.DeliveryLocationProperties;
import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "klickit.delivery.enforce-range=true",
        "klickit.delivery.store.max-radius-km=6.0"
})
public class SpringLiveGeofenceEnforcementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeliveryLocationProperties deliveryLocationProperties;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Product testProduct;
    private final List<User> testUsers = new ArrayList<>();
    private String customerToken;
    private User customerUser;

    @BeforeEach
    void setUp() throws Exception {
        testProduct = productRepository.save(Product.builder()
                .name("Geofence Test Apple")
                .description("Crisp apple for geofence validation")
                .price(new BigDecimal("30.00"))
                .category("Fruits")
                .active(true)
                .build());

        String unique = UUID.randomUUID().toString().substring(0, 8);
        customerUser = User.builder()
                .name("Geofence Customer")
                .email("geo_cust_" + unique + "@klickit.test")
                .phone("+919" + (long) (Math.random() * 900000000L + 100000000L))
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.CUSTOMER)
                .build();
        customerUser = userRepository.save(customerUser);
        testUsers.add(customerUser);

        String loginJson = String.format("{\"email\":\"%s\",\"password\":\"Password123!\"}", customerUser.getEmail());
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        customerToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("token")
                .asText();
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();

        for (User u : testUsers) {
            userRepository.delete(u);
        }
        testUsers.clear();

        if (testProduct != null && testProduct.getId() != null) {
            productRepository.delete(testProduct);
        }
    }

    @Test
    @DisplayName("Verify Spring-managed DeliveryLocationProperties bean has enforceRange=true and binds configured properties")
    void verifySpringManagedBeanReceivesEnforceRangeTrue() {
        assertThat(deliveryLocationProperties).isNotNull();
        assertThat(deliveryLocationProperties.isEnforceRange())
                .as("Spring-managed DeliveryLocationProperties should have enforceRange=true when property is true")
                .isTrue();
        assertThat(deliveryLocationProperties.getMaxRadiusKm()).isEqualTo(6.0);
        assertThat(deliveryLocationProperties.getStoreLatitude()).isCloseTo(23.073427, org.assertj.core.data.Offset.offset(0.001));
        assertThat(deliveryLocationProperties.getStoreLongitude()).isCloseTo(76.828648, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("HTTP Checkout - Inside radius coordinate succeeds with HTTP 201 Created")
    void httpCheckout_insideRadius_succeeds() throws Exception {
        String sessionId = "sess_inside_" + UUID.randomUUID();
        AddToCartRequest addReq = new AddToCartRequest(sessionId, testProduct.getId(), 2);
        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        CheckoutRequest checkoutReq = new CheckoutRequest(
                sessionId,
                customerUser.getName(),
                customerUser.getPhone(),
                "Hostel 3",
                23.075611,
                76.850082,
                "Campus Gate"
        );

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PLACED"))
                .andExpect(jsonPath("$.data.meetAtGate").value(true));
    }

    @Test
    @DisplayName("HTTP Checkout - Outside radius coordinate (Delhi 28.6139, 77.2090) rejected with HTTP 400 Bad Request")
    void httpCheckout_outsideRadius_rejectedWith400() throws Exception {
        String sessionId = "sess_delhi_" + UUID.randomUUID();
        AddToCartRequest addReq = new AddToCartRequest(sessionId, testProduct.getId(), 1);
        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        CheckoutRequest checkoutReq = new CheckoutRequest(
                sessionId,
                customerUser.getName(),
                customerUser.getPhone(),
                "Connaught Place, New Delhi",
                28.6139,
                77.2090,
                null
        );

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("outside our service area")));
    }

    @Test
    @DisplayName("HTTP Checkout - Missing coordinates rejected with HTTP 400 Bad Request")
    void httpCheckout_missingCoordinates_rejectedWith400() throws Exception {
        String sessionId = "sess_missing_" + UUID.randomUUID();
        AddToCartRequest addReq = new AddToCartRequest(sessionId, testProduct.getId(), 1);
        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        CheckoutRequest checkoutReq = new CheckoutRequest(
                sessionId,
                customerUser.getName(),
                customerUser.getPhone(),
                "Somewhere without pin",
                null,
                null,
                null
        );

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Valid delivery latitude and longitude are required"));
    }

    @Test
    @DisplayName("HTTP Checkout - Invalid coordinates (Lat=95.0) rejected with HTTP 400 Bad Request")
    void httpCheckout_invalidCoordinates_rejectedWith400() throws Exception {
        String sessionId = "sess_invalid_" + UUID.randomUUID();
        AddToCartRequest addReq = new AddToCartRequest(sessionId, testProduct.getId(), 1);
        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        CheckoutRequest checkoutReq = new CheckoutRequest(
                sessionId,
                customerUser.getName(),
                customerUser.getPhone(),
                "Invalid North Pole",
                95.0,
                76.850082,
                null
        );

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Latitude must be between -90 and 90, and longitude between -180 and 180"));
    }
}
