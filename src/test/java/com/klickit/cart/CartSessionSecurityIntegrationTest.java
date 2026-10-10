package com.klickit.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.cart.dto.AddToCartRequest;
import com.klickit.cart.dto.RemoveFromCartRequest;
import com.klickit.cart.entity.Cart;
import com.klickit.cart.entity.CartItem;
import com.klickit.cart.repository.CartRepository;
import com.klickit.cart.service.CartService;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.entity.Order;
import com.klickit.order.repository.OrderRepository;
import com.klickit.order.service.OrderService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Cart Session Ownership & Cross-Customer Isolation Security Tests")
class CartSessionSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Product testProduct;
    private final List<User> testUsers = new ArrayList<>();
    private final List<Order> testOrders = new ArrayList<>();

    private String customerAToken;
    private String customerBToken;
    private final String customerAEmail = "customera@example.com";
    private final String customerBEmail = "customerb@example.com";

    @BeforeEach
    void setUp() throws Exception {
        cartRepository.deleteAll();
        for (Order o : testOrders) {
            orderRepository.delete(o);
        }
        testOrders.clear();
        for (User u : testUsers) {
            userRepository.delete(u);
        }
        testUsers.clear();

        testProduct = Product.builder()
                .name("Security Test Item")
                .price(new BigDecimal("100.00"))
                .category("Groceries")
                .active(true)
                .stock(50)
                .build();
        testProduct = productRepository.save(testProduct);

        customerAToken = createAndLoginUser("Customer A", customerAEmail, "+919876543201", "password123");
        customerBToken = createAndLoginUser("Customer B", customerBEmail, "+919876543202", "password123");
    }

    @AfterEach
    void tearDown() {
        cartRepository.deleteAll();
        for (Order o : testOrders) {
            orderRepository.delete(o);
        }
        testOrders.clear();
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
    @DisplayName("1. Anonymous guest can create, retrieve, add, and remove items without authentication")
    void guestCartOperations_allowNormalGuestAccess() throws Exception {
        String guestSessionId = "guest_sess_" + UUID.randomUUID();

        // 1. Add item to cart anonymously
        AddToCartRequest addReq = new AddToCartRequest(guestSessionId, testProduct.getId(), 2);
        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.sessionId", is(guestSessionId)))
                .andExpect(jsonPath("$.data.itemCount", is(2)));

        // Verify cart in DB has customer_email == null
        Cart cartInDb = cartRepository.findBySessionId(guestSessionId).orElseThrow();
        assertThat(cartInDb.getCustomerEmail()).isNull();

        // 2. Retrieve cart anonymously
        mockMvc.perform(get("/cart/" + guestSessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount", is(2)));

        // 3. Remove item anonymously
        RemoveFromCartRequest remReq = new RemoveFromCartRequest(guestSessionId, testProduct.getId());
        mockMvc.perform(post("/cart/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(remReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount", is(0)));
    }

    @Test
    @DisplayName("2. First authenticated access by Customer A claims an unowned guest cart")
    void firstAuthenticatedAccess_claimsUnownedCart() throws Exception {
        String sessionId = "unowned_sess_" + UUID.randomUUID();

        // Guest creates cart
        AddToCartRequest addReq = new AddToCartRequest(sessionId, testProduct.getId(), 1);
        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        assertThat(cartRepository.findBySessionId(sessionId).orElseThrow().getCustomerEmail()).isNull();

        // Customer A reads the cart with Bearer token
        mockMvc.perform(get("/cart/" + sessionId)
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk());

        // Verify cart is now claimed by Customer A
        Cart claimedCart = cartRepository.findBySessionId(sessionId).orElseThrow();
        assertThat(claimedCart.getCustomerEmail()).isEqualTo(customerAEmail.toLowerCase());
    }

    @Test
    @DisplayName("3. Same Customer A can read, mutate, and reload their claimed cart")
    void sameCustomerAccess_allowsReadAndMutation() throws Exception {
        String sessionId = "customera_sess_" + UUID.randomUUID();

        // Customer A adds item
        AddToCartRequest addReq = new AddToCartRequest(sessionId, testProduct.getId(), 2);
        mockMvc.perform(post("/cart/add")
                        .header("Authorization", "Bearer " + customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount", is(2)));

        // Customer A reads cart
        mockMvc.perform(get("/cart/" + sessionId)
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount", is(2)));

        // Customer A adds another
        mockMvc.perform(post("/cart/add")
                        .header("Authorization", "Bearer " + customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount", is(4)));
    }

    @Test
    @DisplayName("4. Cross-Customer reads, additions, and removals are rejected with 403 Forbidden")
    void crossCustomerAccess_rejectedWith403() throws Exception {
        String customerASessionId = "customera_private_" + UUID.randomUUID();

        // Customer A creates cart
        AddToCartRequest addReq = new AddToCartRequest(customerASessionId, testProduct.getId(), 2);
        mockMvc.perform(post("/cart/add")
                        .header("Authorization", "Bearer " + customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        // Customer B attempts to READ Customer A's cart -> 403 Forbidden
        mockMvc.perform(get("/cart/" + customerASessionId)
                        .header("Authorization", "Bearer " + customerBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Access denied")));

        // Customer B attempts to ADD to Customer A's cart -> 403 Forbidden
        AddToCartRequest tamperAdd = new AddToCartRequest(customerASessionId, testProduct.getId(), 5);
        mockMvc.perform(post("/cart/add")
                        .header("Authorization", "Bearer " + customerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tamperAdd)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));

        // Customer B attempts to REMOVE from Customer A's cart -> 403 Forbidden
        RemoveFromCartRequest tamperRem = new RemoveFromCartRequest(customerASessionId, testProduct.getId());
        mockMvc.perform(post("/cart/remove")
                        .header("Authorization", "Bearer " + customerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tamperRem)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));

        // Verify Customer A's cart remains intact in DB (quantity is still 2)
        mockMvc.perform(get("/cart/" + customerASessionId)
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount", is(2)));
    }

    @Test
    @DisplayName("5. Anonymous guest access to a customer-owned cart is rejected with 403 Forbidden")
    void anonymousGuestAccess_toCustomerOwnedCart_rejectedWith403() throws Exception {
        String sessionId = "owned_by_a_" + UUID.randomUUID();

        AddToCartRequest addReq = new AddToCartRequest(sessionId, testProduct.getId(), 3);
        mockMvc.perform(post("/cart/add")
                        .header("Authorization", "Bearer " + customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        // Anonymous request to GET -> 403 Forbidden
        mockMvc.perform(get("/cart/" + sessionId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));

        // Anonymous request to ADD -> 403 Forbidden
        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("6. Checkout hijacking: Customer B cannot checkout Customer A's cart; inventory and cart remain unchanged")
    void checkoutHijacking_rejectedAndLeavesCartAndInventoryUnchanged() throws Exception {
        String customerASessionId = "checkout_hijack_sess_" + UUID.randomUUID();

        // Customer A populates cart with 5 units
        AddToCartRequest addReq = new AddToCartRequest(customerASessionId, testProduct.getId(), 5);
        mockMvc.perform(post("/cart/add")
                        .header("Authorization", "Bearer " + customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        int initialStock = productRepository.findById(testProduct.getId()).orElseThrow().getStock();
        assertThat(initialStock).isEqualTo(50);

        // Customer B attempts to check out Customer A's cart
        CheckoutRequest hijackCheckout = new CheckoutRequest(
                customerASessionId,
                "Customer B Attacker",
                "+919876543202",
                "Campus Gate",
                null
        );

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + customerBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hijackCheckout)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));

        // Verify stock is STILL 50 (not decremented)
        Product afterAttemptProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(afterAttemptProduct.getStock()).isEqualTo(50);

        // Verify Customer A's cart still has 5 items (not cleared)
        mockMvc.perform(get("/cart/" + customerASessionId)
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount", is(5)));

        Cart customerACart = cartRepository.findBySessionId(customerASessionId).orElseThrow();
        assertThat(customerACart.getCustomerEmail()).isEqualTo(customerAEmail);
    }

    @Test
    @DisplayName("7. Legitimate checkout: Customer A successfully checks out their own cart")
    void legitimateCheckout_succeedsForCartOwner() throws Exception {
        String sessionId = "legit_checkout_" + UUID.randomUUID();

        AddToCartRequest addReq = new AddToCartRequest(sessionId, testProduct.getId(), 2);
        mockMvc.perform(post("/cart/add")
                        .header("Authorization", "Bearer " + customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk());

        CheckoutRequest legitCheckout = new CheckoutRequest(
                sessionId,
                "Customer A Owner",
                "+919876543201",
                "Campus Gate",
                null
        );

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(legitCheckout)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.customerEmail", is(customerAEmail)));

        // Verify stock decremented to 48
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(48);
    }

    @Test
    @DisplayName("8. Legacy carts with customer_email = NULL remain usable by guests until claimed")
    void legacyCartsWithNullEmail_remainUsable() throws Exception {
        String legacySessionId = "legacy_null_email_" + UUID.randomUUID();
        Cart legacyCart = Cart.builder()
                .sessionId(legacySessionId)
                .customerEmail(null)
                .build();
        legacyCart.addItem(CartItem.builder()
                .productId(testProduct.getId())
                .productName(testProduct.getName())
                .quantity(1)
                .unitPrice(testProduct.getPrice())
                .build());
        cartRepository.save(legacyCart);

        // Anonymous read succeeds
        mockMvc.perform(get("/cart/" + legacySessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount", is(1)));

        // Authenticated customer claims it on checkout
        CheckoutRequest checkoutReq = new CheckoutRequest(
                legacySessionId,
                "Customer A",
                "+919876543201",
                "Campus Gate",
                null
        );

        mockMvc.perform(post("/orders/checkout")
                        .header("Authorization", "Bearer " + customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isCreated());
    }
}
