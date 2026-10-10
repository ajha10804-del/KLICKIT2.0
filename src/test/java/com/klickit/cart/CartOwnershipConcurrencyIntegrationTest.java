package com.klickit.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.cart.entity.Cart;
import com.klickit.cart.entity.CartItem;
import com.klickit.cart.repository.CartRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Cart Ownership Concurrency Hardening Integration Tests")
class CartOwnershipConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Product testProduct;
    private final List<User> testUsers = new ArrayList<>();

    private String customerAToken;
    private String customerBToken;
    private final String customerAEmail = "concurrency.a@example.com";
    private final String customerBEmail = "concurrency.b@example.com";

    @BeforeEach
    void setUp() throws Exception {
        cartRepository.deleteAll();
        for (User u : testUsers) {
            userRepository.delete(u);
        }
        testUsers.clear();

        testProduct = Product.builder()
                .name("Concurrency Test Item")
                .price(new BigDecimal("150.00"))
                .category("Beverages")
                .active(true)
                .stock(100)
                .build();
        testProduct = productRepository.save(testProduct);

        customerAToken = createAndLoginUser("Customer A", customerAEmail, "+919876543301", "password123");
        customerBToken = createAndLoginUser("Customer B", customerBEmail, "+919876543302", "password123");
    }

    @AfterEach
    void tearDown() {
        cartRepository.deleteAll();
        for (User u : testUsers) {
            userRepository.delete(u);
        }
        testUsers.clear();
        if (testProduct != null && testProduct.getId() != null) {
            productRepository.delete(testProduct);
        }
    }

    private String createAndLoginUser(String name, String email, String phone, String password) throws Exception {
        User user = User.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(password))
                .role(Role.CUSTOMER)
                .build();
        User saved = userRepository.save(user);
        testUsers.add(saved);

        String loginJson = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    @Test
    @DisplayName("Concurrent Claim Race: Exactly one customer succeeds in claiming an unowned cart, losing customer is rejected")
    void concurrentClaim_exactlyOneCustomerSucceeds_losingCustomerRejected() throws Exception {
        String sessionId = "concurrent_race_" + UUID.randomUUID();

        // 1. Seed unowned guest cart with 2 items in database (customer_email = null)
        Cart guestCart = Cart.builder()
                .sessionId(sessionId)
                .customerEmail(null)
                .build();
        guestCart.addItem(CartItem.builder()
                .productId(testProduct.getId())
                .productName(testProduct.getName())
                .quantity(2)
                .unitPrice(testProduct.getPrice())
                .build());
        cartRepository.save(guestCart);

        // 2. Prepare concurrent threads for Customer A and Customer B
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger forbiddenCount = new AtomicInteger(0);

        Future<?> taskA = executor.submit(() -> {
            try {
                startSignal.await();
                MvcResult res = mockMvc.perform(get("/cart/" + sessionId)
                                .header("Authorization", "Bearer " + customerAToken))
                        .andReturn();
                int status = res.getResponse().getStatus();
                if (status == 200) {
                    successCount.incrementAndGet();
                } else if (status == 403) {
                    forbiddenCount.incrementAndGet();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> taskB = executor.submit(() -> {
            try {
                startSignal.await();
                MvcResult res = mockMvc.perform(get("/cart/" + sessionId)
                                .header("Authorization", "Bearer " + customerBToken))
                        .andReturn();
                int status = res.getResponse().getStatus();
                if (status == 200) {
                    successCount.incrementAndGet();
                } else if (status == 403) {
                    forbiddenCount.incrementAndGet();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Fire both threads simultaneously
        startSignal.countDown();
        taskA.get(10, TimeUnit.SECONDS);
        taskB.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 3. Exactly one customer succeeded (200 OK) or one won immediately
        Cart finalCart = cartRepository.findBySessionId(sessionId).orElseThrow();
        String finalOwner = finalCart.getCustomerEmail();
        assertThat(finalOwner).isNotNull();
        assertThat(finalOwner).isIn(customerAEmail, customerBEmail);

        // 4. Verify the winning customer can read their cart, and the losing customer receives 403 Forbidden
        String winningToken = finalOwner.equalsIgnoreCase(customerAEmail) ? customerAToken : customerBToken;
        String losingToken = finalOwner.equalsIgnoreCase(customerAEmail) ? customerBToken : customerAToken;

        mockMvc.perform(get("/cart/" + sessionId)
                        .header("Authorization", "Bearer " + winningToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount", is(2)));

        mockMvc.perform(get("/cart/" + sessionId)
                        .header("Authorization", "Bearer " + losingToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Access denied")));
    }
}
