package com.klickit.cart;

import com.klickit.cart.dto.AddToCartRequest;
import com.klickit.cart.dto.CartResponse;
import com.klickit.cart.entity.Cart;
import com.klickit.cart.entity.CartItem;
import com.klickit.cart.repository.CartRepository;
import com.klickit.cart.service.CartCleanupService;
import com.klickit.cart.service.CartService;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.service.OrderService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class CartLifecycleAndCleanupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartCleanupService cartCleanupService;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        cartRepository.deleteAll();
        productRepository.deleteAll();

        testProduct = Product.builder()
                .name("Lifecycle Test Product")
                .price(new BigDecimal("50.00"))
                .category("Snacks")
                .active(true)
                .stock(100)
                .build();
        testProduct = productRepository.save(testProduct);
    }

    @AfterEach
    void tearDown() {
        cartRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("Abandoned Cart Eviction: Purges carts older than retention threshold and retains active recent carts")
    void abandonedCartPurge_deletesStaleCartsAndPreservesRecentOnes() {
        Instant now = Instant.now();

        // 1. Seed Stale Cart (updated 45 days ago)
        Cart staleCart = Cart.builder()
                .sessionId("stale_session_45d")
                .build();
        staleCart.addItem(CartItem.builder()
                .productId(testProduct.getId())
                .productName(testProduct.getName())
                .quantity(3)
                .unitPrice(testProduct.getPrice())
                .build());
        Cart savedStale = cartRepository.save(staleCart);

        // Update updatedAt to 45 days ago via JDBC (bypassing auditing override)
        Instant staleTime = now.minus(45, ChronoUnit.DAYS);
        jdbcTemplate.update("UPDATE carts SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(staleTime), savedStale.getId());

        // 2. Seed Recent Cart (updated 2 days ago)
        Cart recentCart = Cart.builder()
                .sessionId("recent_session_2d")
                .build();
        recentCart.addItem(CartItem.builder()
                .productId(testProduct.getId())
                .productName(testProduct.getName())
                .quantity(1)
                .unitPrice(testProduct.getPrice())
                .build());
        Cart savedRecent = cartRepository.save(recentCart);

        Instant recentTime = now.minus(2, ChronoUnit.DAYS);
        jdbcTemplate.update("UPDATE carts SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(recentTime), savedRecent.getId());

        // Verify initial state
        assertThat(cartRepository.findAll()).hasSize(2);

        // 3. Trigger 30-day purge
        int purgedCount = cartCleanupService.purgeAbandonedCarts(30);
        assertThat(purgedCount).isEqualTo(1);

        // 4. Verify stale cart and its items are deleted (cascade delete), recent cart remains
        assertThat(cartRepository.findBySessionId("stale_session_45d")).isEmpty();
        assertThat(cartRepository.findBySessionId("recent_session_2d")).isPresent();

        // Verify line items for stale cart were removed from cart_items table
        Integer staleItemCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_items WHERE cart_id = ?",
                Integer.class,
                savedStale.getId());
        assertThat(staleItemCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Abandoned Cart Eviction: Clamps zero or negative retentionDays to safe minimum of 1 day")
    void abandonedCartPurge_clampsNegativeOrZeroRetentionDays() {
        // Carts created today (0 days old) should NEVER be purged even if 0 or negative retention days requested
        Cart todayCart = Cart.builder()
                .sessionId("today_cart_session")
                .build();
        todayCart.addItem(CartItem.builder()
                .productId(testProduct.getId())
                .productName(testProduct.getName())
                .quantity(1)
                .unitPrice(testProduct.getPrice())
                .build());
        cartRepository.save(todayCart);

        // Calling purge with 0 or negative days
        int purgedWithZero = cartCleanupService.purgeAbandonedCarts(0);
        int purgedWithNegative = cartCleanupService.purgeAbandonedCarts(-15);

        assertThat(purgedWithZero).isEqualTo(0);
        assertThat(purgedWithNegative).isEqualTo(0);
        assertThat(cartRepository.findBySessionId("today_cart_session")).isPresent();
    }

    @Test
    @DisplayName("Scheduled Cart Cleanup: scheduledAbandonedCartCleanup executes without exception")
    void scheduledAbandonedCartCleanup_executesCleanly() {
        // Verify scheduled method entrypoint executes cleanly
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                cartCleanupService.scheduledAbandonedCartCleanup()
        );
    }

    @Test
    @DisplayName("Session ID Validation: Rejects blank, empty, and excessively long session IDs")
    void sessionIdValidation_rejectsInvalidSessionIds() throws Exception {
        // 1. Blank string session ID on POST /cart/add
        String blankJson = String.format("{\"sessionId\":\"   \",\"productId\":\"%s\",\"quantity\":1}",
                testProduct.getId());

        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blankJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));

        // 2. Overlong session ID (> 64 chars)
        String overlongId = "a".repeat(65);
        String overlongJson = String.format("{\"sessionId\":\"%s\",\"productId\":\"%s\",\"quantity\":1}",
                overlongId, testProduct.getId());

        mockMvc.perform(post("/cart/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overlongJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));

        // 3. Overlong session ID on GET /cart/{sessionId}
        mockMvc.perform(get("/cart/" + overlongId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @WithMockUser(username = "shopper@example.com", roles = {"CUSTOMER"})
    @DisplayName("Checkout Non-Interference: Valid session cart proceeds to checkout and decrements inventory cleanly")
    void checkoutFlow_unaffectedByCartLifecycleHardening() {
        String sessionId = "valid_cart_session_123";

        // 1. Add item to cart
        CartResponse cartResponse = cartService.addToCart(new AddToCartRequest(sessionId, testProduct.getId(), 2));
        assertThat(cartResponse.getItems()).hasSize(1);
        assertThat(cartResponse.getItemCount()).isEqualTo(2);

        // 2. Checkout
        CheckoutRequest checkoutReq = new CheckoutRequest(
                sessionId,
                "Regular Shopper",
                "+919876543210",
                "Hostel Block 1",
                null
        );

        OrderResponse orderResponse = orderService.checkout(checkoutReq);
        assertThat(orderResponse).isNotNull();
        assertThat(orderResponse.getId()).isNotNull();

        // 3. Verify stock decremented from 100 to 98
        Product updatedProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(98);

        // 4. Verify cart items cleared
        CartResponse cartAfter = cartService.getCart(sessionId);
        assertThat(cartAfter.getItems()).isEmpty();
    }
}
