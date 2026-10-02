package com.klickit.order;

import com.klickit.cart.entity.Cart;
import com.klickit.cart.entity.CartItem;
import com.klickit.cart.repository.CartRepository;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.repository.OrderRepository;
import com.klickit.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceAuthorizationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String email, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Customer A CAN access their own order (200 OK)")
    void customerA_canAccessOwnOrder() {
        authenticate("customerA@test.com", "CUSTOMER");

        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customerA@test.com");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getCustomerEmail()).isEqualTo("customerA@test.com");
    }

    @Test
    @DisplayName("Customer A CANNOT access Customer B's order (throws AccessDeniedException)")
    void customerA_cannotAccessCustomerBOrder() {
        authenticate("customerA@test.com", "CUSTOMER");

        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customerB@test.com");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to access this order");
    }

    @Test
    @DisplayName("ADMIN can access any customer's order")
    void admin_canAccessAnyOrder() {
        authenticate("admin@klickit.com", "ADMIN");

        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customerB@test.com");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getCustomerEmail()).isEqualTo("customerB@test.com");
    }

    @Test
    @DisplayName("Anonymous caller cannot access order details")
    void anonymous_cannotAccessOrder() {
        SecurityContextHolder.clearContext();

        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customerA@test.com");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    @DisplayName("Customer A cannot cancel Customer B's order")
    void customerA_cannotCancelCustomerBOrder() {
        authenticate("customerA@test.com", "CUSTOMER");

        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customerB@test.com");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to access this order");
    }

    @Test
    @DisplayName("OrderService derives customerEmail strictly from authenticated principal, ignoring request body email")
    void checkout_derivesCustomerEmailFromPrincipal_ignoresRequestBody() {
        authenticate("customerA@test.com", "CUSTOMER");

        String sessionId = "sess_test_123";
        Cart cart = Cart.builder().sessionId(sessionId).items(new ArrayList<>()).build();
        cart.addItem(CartItem.builder()
                .cart(cart)
                .productId(UUID.randomUUID())
                .productName("Item 1")
                .unitPrice(new BigDecimal("50.00"))
                .quantity(1)
                .build());

        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "Customer A", "9876543210", "Address 100", null, "attacker@fake.com"
        );

        OrderResponse response = orderService.checkout(request);

        assertThat(response).isNotNull();
        assertThat(response.getCustomerEmail()).isEqualTo("customerA@test.com");
        assertThat(response.getCustomerEmail()).isNotEqualTo("attacker@fake.com");
    }

    private Order createOrder(UUID id, String customerEmail) {
        Order order = Order.builder()
                .customerName("Test Customer")
                .customerPhone("9876543210")
                .customerAddress("Campus Block A")
                .customerEmail(customerEmail)
                .totalAmount(new BigDecimal("100.00"))
                .status(OrderStatus.PLACED)
                .items(new ArrayList<>())
                .build();
        order.setId(id);
        return order;
    }
}
