package com.klickit.order;

import com.klickit.cart.dto.AddToCartRequest;
import com.klickit.cart.entity.Cart;
import com.klickit.cart.repository.CartRepository;
import com.klickit.cart.service.CartService;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderItem;
import com.klickit.order.entity.OrderStatus;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "klickit.delivery.enforce-range=false"
})
public class InventoryOrderLifecycleIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Product testProduct;
    private User customerUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();

        String unique = UUID.randomUUID().toString().substring(0, 8);
        customerUser = userRepository.save(User.builder()
                .name("Inventory Customer")
                .email("inv_cust_" + unique + "@klickit.test")
                .phone("+919" + (long) (Math.random() * 900000000L + 100000000L))
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.CUSTOMER)
                .build());

        adminUser = userRepository.save(User.builder()
                .name("Inventory Admin")
                .email("inv_admin_" + unique + "@klickit.test")
                .phone("+919" + (long) (Math.random() * 900000000L + 100000000L))
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ADMIN)
                .build());

        testProduct = productRepository.save(Product.builder()
                .name("Inventory Test Widget " + unique)
                .description("Special widget for inventory testing")
                .price(new BigDecimal("50.00"))
                .category("Widgets")
                .active(true)
                .stock(5)
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        if (testProduct != null && testProduct.getId() != null) {
            productRepository.delete(testProduct);
        }
        if (customerUser != null && customerUser.getId() != null) {
            userRepository.delete(customerUser);
        }
        if (adminUser != null && adminUser.getId() != null) {
            userRepository.delete(adminUser);
        }
    }

    private void authenticate(User user) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Product defaults: new product without explicit stock receives 50 units")
    void productDefaultStock_is50() {
        Product p = Product.builder()
                .name("Default Stock Item " + UUID.randomUUID())
                .price(new BigDecimal("25.00"))
                .category("Snacks")
                .build();
        Product saved = productRepository.save(p);

        assertThat(saved.getStock()).isEqualTo(50);
        productRepository.delete(saved);
    }

    @Test
    @DisplayName("Successful checkout decrements stock and stores productId on OrderItem")
    void checkout_successful_decrementsStockAndStoresProductId() {
        authenticate(customerUser);
        String sessionId = "sess_success_" + UUID.randomUUID();

        cartService.addToCart(new AddToCartRequest(sessionId, testProduct.getId(), 2));

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "Customer", "9876543210", "Campus Hostel", null
        );

        OrderResponse response = orderService.checkout(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductId()).isEqualTo(testProduct.getId());

        Product reloaded = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(reloaded.getStock()).isEqualTo(3); // 5 - 2 = 3
    }

    @Test
    @DisplayName("Insufficient stock fails checkout with clear message and leaves stock intact")
    void checkout_insufficientStock_failsAndPreservesStock() {
        authenticate(customerUser);
        String sessionId = "sess_insufficient_" + UUID.randomUUID();

        // Stock is 5, requested is 6 (within max line limit of 20)
        cartService.addToCart(new AddToCartRequest(sessionId, testProduct.getId(), 6));

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "Customer", "9876543210", "Campus Hostel", null
        );

        assertThatThrownBy(() -> orderService.checkout(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient stock for product");

        Product reloaded = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(reloaded.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("Inactive product cannot be checked out")
    void checkout_inactiveProduct_fails() {
        authenticate(customerUser);
        String sessionId = "sess_inactive_" + UUID.randomUUID();
        cartService.addToCart(new AddToCartRequest(sessionId, testProduct.getId(), 1));

        // Deactivate product after adding to cart
        testProduct.setActive(false);
        productRepository.save(testProduct);

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "Customer", "9876543210", "Campus Hostel", null
        );

        assertThatThrownBy(() -> orderService.checkout(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Product is no longer available");

        Product reloaded = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(reloaded.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("Multi-item checkout rolls back all decrements when any item has insufficient stock")
    void checkout_multiItem_rollsBackAllDecrementsOnFailure() {
        authenticate(customerUser);

        Product itemA = productRepository.save(Product.builder()
                .name("Multi Item A " + UUID.randomUUID())
                .price(new BigDecimal("10.00"))
                .category("Test")
                .stock(10)
                .build());

        Product itemB = productRepository.save(Product.builder()
                .name("Multi Item B " + UUID.randomUUID())
                .price(new BigDecimal("10.00"))
                .category("Test")
                .stock(1) // Only 1 available
                .build());

        String sessionId = "sess_multi_" + UUID.randomUUID();
        cartService.addToCart(new AddToCartRequest(sessionId, itemA.getId(), 3));
        cartService.addToCart(new AddToCartRequest(sessionId, itemB.getId(), 2)); // Requests 2 of itemB

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "Customer", "9876543210", "Campus Hostel", null
        );

        assertThatThrownBy(() -> orderService.checkout(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient stock");

        // Both items should retain original stock
        Product reloadedA = productRepository.findById(itemA.getId()).orElseThrow();
        Product reloadedB = productRepository.findById(itemB.getId()).orElseThrow();
        assertThat(reloadedA.getStock()).isEqualTo(10);
        assertThat(reloadedB.getStock()).isEqualTo(1);

        productRepository.delete(itemA);
        productRepository.delete(itemB);
    }

    @Test
    @DisplayName("Customer cancellation restores stock exactly once")
    void cancelOrder_restoresStockExactlyOnce() {
        authenticate(customerUser);
        String sessionId = "sess_cancel_" + UUID.randomUUID();
        cartService.addToCart(new AddToCartRequest(sessionId, testProduct.getId(), 2));

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "Customer", "9876543210", "Campus Hostel", null
        );
        OrderResponse placed = orderService.checkout(request);

        Product postCheckout = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(postCheckout.getStock()).isEqualTo(3);

        OrderResponse cancelled = orderService.cancelOrder(placed.getId());
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        Product postCancel = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(postCancel.getStock()).isEqualTo(5); // Restored 3 + 2 = 5

        // Duplicate cancellation attempt must fail and not restore stock again
        assertThatThrownBy(() -> orderService.cancelOrder(placed.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel an already cancelled order");

        Product postSecondCancel = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(postSecondCancel.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("Admin rejection of PLACED order restores stock exactly once")
    void rejectOrder_restoresStockExactlyOnce() {
        authenticate(customerUser);
        String sessionId = "sess_reject_" + UUID.randomUUID();
        cartService.addToCart(new AddToCartRequest(sessionId, testProduct.getId(), 3));

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "Customer", "9876543210", "Campus Hostel", null
        );
        OrderResponse placed = orderService.checkout(request);

        Product postCheckout = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(postCheckout.getStock()).isEqualTo(2);

        // Admin rejects order
        authenticate(adminUser);
        OrderResponse rejected = orderService.rejectOrder(placed.getId());
        assertThat(rejected.getStatus()).isEqualTo(OrderStatus.REJECTED);

        Product postReject = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(postReject.getStock()).isEqualTo(5); // Restored 2 + 3 = 5

        // Duplicate rejection must fail
        assertThatThrownBy(() -> orderService.rejectOrder(placed.getId()))
                .isInstanceOf(IllegalStateException.class);

        Product postSecondReject = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(postSecondReject.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("Cancellation from OUT_FOR_DELIVERY restores stock")
    void cancelOrder_fromOutForDelivery_restoresStock() {
        authenticate(customerUser);
        String sessionId = "sess_ofd_" + UUID.randomUUID();
        cartService.addToCart(new AddToCartRequest(sessionId, testProduct.getId(), 2));

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "Customer", "9876543210", "Campus Hostel", null
        );
        OrderResponse placed = orderService.checkout(request);

        // Transition order manually to OUT_FOR_DELIVERY to simulate delivery lifecycle
        Order order = orderRepository.findById(placed.getId()).orElseThrow();
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        orderRepository.save(order);

        // Customer cancels order from OUT_FOR_DELIVERY
        authenticate(customerUser);
        OrderResponse cancelled = orderService.cancelOrder(placed.getId());
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        Product reloaded = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(reloaded.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("Historical order item with null productId skips stock restoration safely")
    void cancelOrder_historicalOrderWithNullProductId_doesNotCrash() {
        authenticate(customerUser);

        Order historicalOrder = Order.builder()
                .customerName("Historical Customer")
                .customerPhone("9876543210")
                .customerAddress("Old Address")
                .customerEmail(customerUser.getEmail())
                .totalAmount(new BigDecimal("100.00"))
                .status(OrderStatus.PLACED)
                .build();

        OrderItem itemWithoutProductId = OrderItem.builder()
                .productName("Ancient Product")
                .productId(null) // Historical null productId
                .quantity(3)
                .price(new BigDecimal("30.00"))
                .build();
        historicalOrder.addItem(itemWithoutProductId);

        Order saved = orderRepository.save(historicalOrder);

        // Cancelling should succeed without error and without NPE
        OrderResponse cancelled = orderService.cancelOrder(saved.getId());
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Terminal orders (DELIVERED) do not permit cancellation and do not touch stock")
    void cancelOrder_deliveredOrder_failsAndPreservesStock() {
        authenticate(customerUser);
        String sessionId = "sess_deliv_" + UUID.randomUUID();
        cartService.addToCart(new AddToCartRequest(sessionId, testProduct.getId(), 1));

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "Customer", "9876543210", "Campus Hostel", null
        );
        OrderResponse placed = orderService.checkout(request);

        Order order = orderRepository.findById(placed.getId()).orElseThrow();
        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);

        assertThatThrownBy(() -> orderService.cancelOrder(placed.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel a delivered order");

        Product reloaded = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(reloaded.getStock()).isEqualTo(4); // Decremented from 5 to 4 at checkout, remains 4
    }

    // =========================================================================
    // Real Concurrency Integration Test: Final Unit Contention
    // =========================================================================

    @Test
    @DisplayName("Ten concurrent checkouts for final single unit: exactly 1 succeeds, 9 fail with 400, final stock 0")
    void concurrentCheckout_finalSingleUnit_exactlyOneSucceeds() throws Exception {
        // Set test product stock to exactly 1
        testProduct.setStock(1);
        productRepository.save(testProduct);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> failureMessages = Collections.synchronizedList(new ArrayList<>());

        // Prepare 10 distinct customer users and sessions
        List<User> concurrentUsers = new ArrayList<>();
        List<String> sessionIds = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            String uSuffix = UUID.randomUUID().toString().substring(0, 8);
            User u = userRepository.save(User.builder()
                    .name("Concurrent User " + i)
                    .email("conc_user_" + uSuffix + "@klickit.test")
                    .phone("+919" + (long) (Math.random() * 900000000L + 100000000L))
                    .password(passwordEncoder.encode("Password123!"))
                    .role(Role.CUSTOMER)
                    .build());
            concurrentUsers.add(u);

            String sId = "sess_conc_" + uSuffix;
            sessionIds.add(sId);

            // Each user has their own cart with 1 unit of testProduct
            cartService.addToCart(new AddToCartRequest(sId, testProduct.getId(), 1));
        }

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                User user = concurrentUsers.get(idx);
                String sId = sessionIds.get(idx);
                readyLatch.countDown();
                try {
                    startLatch.await(); // Simultaneous release

                    // Authenticate in current thread
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    CheckoutRequest req = new CheckoutRequest(
                            sId, "User " + idx, "9876543210", "Hostel Room", null
                    );
                    orderService.checkout(req);
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                    failureMessages.add(ex.getMessage());
                } finally {
                    SecurityContextHolder.clearContext();
                    doneLatch.countDown();
                }
            });
        }

        // Wait until all threads are ready
        readyLatch.await(5, TimeUnit.SECONDS);
        // Start all threads at once
        startLatch.countDown();
        // Wait for all to finish
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get())
                .as("Exactly one checkout must succeed for the single available unit")
                .isEqualTo(1);
        assertThat(failureCount.get())
                .as("Remaining nine checkouts must fail due to insufficient stock")
                .isEqualTo(9);

        for (String msg : failureMessages) {
            assertThat(msg).contains("Insufficient stock for product");
        }

        // Verify database state: final stock is exactly 0 and never negative
        Product finalProduct = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(finalProduct.getStock())
                .as("Final stock must be exactly 0")
                .isEqualTo(0);

        // Cleanup concurrent test users
        for (User u : concurrentUsers) {
            userRepository.delete(u);
        }
    }
}
