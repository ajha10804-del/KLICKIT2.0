package com.klickit.order;

import com.klickit.cart.repository.CartRepository;
import com.klickit.delivery.entity.DeliveryPartner;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.delivery.service.DeliveryService;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.repository.OrderRepository;
import com.klickit.order.service.OrderService;
import com.klickit.product.repository.ProductRepository;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.crypto.password.PasswordEncoder;

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
public class OrderCancellationSecurityAndStateTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private OrderService orderService;

    private DeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new DeliveryService(deliveryPartnerRepository, orderRepository, userRepository, passwordEncoder);
    }

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

    private Order createOrder(UUID id, String customerEmail, OrderStatus status, UUID partnerId) {
        Order order = Order.builder()
                .customerName("Test Customer")
                .customerPhone("9876543210")
                .customerAddress("Block A, Room 101")
                .customerEmail(customerEmail)
                .totalAmount(new BigDecimal("100.00"))
                .deliveryFee(new BigDecimal("25.00"))
                .status(status)
                .deliveryPartnerId(partnerId)
                .items(new ArrayList<>())
                .build();
        order.setId(id);
        return order;
    }

    // =========================================================================
    // 1. Valid Customer Cancellation
    // =========================================================================

    @Test
    @DisplayName("Authorized customer can cancel their own PLACED order")
    void customer_canCancelOwnPlacedOrder_success() {
        authenticate("customer@klickit.com", "CUSTOMER");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.PLACED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Authorized customer can cancel their own ASSIGNED order")
    void customer_canCancelOwnAssignedOrder_success() {
        authenticate("customer@klickit.com", "CUSTOMER");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.ASSIGNED, UUID.randomUUID());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Authorized customer can cancel their own OUT_FOR_DELIVERY order")
    void customer_canCancelOwnOutForDeliveryOrder_success() {
        authenticate("customer@klickit.com", "CUSTOMER");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.OUT_FOR_DELIVERY, UUID.randomUUID());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // =========================================================================
    // 2. Cancellation of an Already-Cancelled Order
    // =========================================================================

    @Test
    @DisplayName("Customer cannot cancel an order that is already CANCELLED")
    void customer_cannotCancelAlreadyCancelledOrder_throwsIllegalStateException() {
        authenticate("customer@klickit.com", "CUSTOMER");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.CANCELLED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel an already cancelled order");
    }

    @Test
    @DisplayName("Admin cannot cancel an order that is already CANCELLED")
    void admin_cannotCancelAlreadyCancelledOrder_throwsIllegalStateException() {
        authenticate("admin@klickit.com", "ADMIN");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.CANCELLED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel an already cancelled order");
    }

    // =========================================================================
    // 3. Cancellation of a Delivered Order
    // =========================================================================

    @Test
    @DisplayName("Customer cannot cancel a DELIVERED order")
    void customer_cannotCancelDeliveredOrder_throwsIllegalStateException() {
        authenticate("customer@klickit.com", "CUSTOMER");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.DELIVERED, UUID.randomUUID());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel a delivered order");
    }

    @Test
    @DisplayName("Admin cannot cancel a DELIVERED order")
    void admin_cannotCancelDeliveredOrder_throwsIllegalStateException() {
        authenticate("admin@klickit.com", "ADMIN");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.DELIVERED, UUID.randomUUID());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel a delivered order");
    }

    // =========================================================================
    // 4. Assigned Driver Attempting Cancellation
    // =========================================================================

    @Test
    @DisplayName("Assigned delivery partner cannot cancel an order, even if assigned to it")
    void assignedDeliveryPartner_cannotCancelOrder_throwsAccessDeniedException() {
        authenticate("driverA@klickit.com", "DELIVERY_PARTNER");
        UUID partnerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.ASSIGNED, partnerId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Delivery partners are not authorized to cancel orders");
    }

    @Test
    @DisplayName("Assigned delivery partner cannot cancel order when OUT_FOR_DELIVERY")
    void assignedDeliveryPartner_outForDelivery_cannotCancelOrder_throwsAccessDeniedException() {
        authenticate("driverA@klickit.com", "DELIVERY_PARTNER");
        UUID partnerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.OUT_FOR_DELIVERY, partnerId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Delivery partners are not authorized to cancel orders");
    }

    // =========================================================================
    // 5. Wrong / Unassigned Driver Attempting Cancellation
    // =========================================================================

    @Test
    @DisplayName("Unassigned delivery partner cannot cancel an order")
    void unassignedDeliveryPartner_cannotCancelOrder_throwsAccessDeniedException() {
        authenticate("driverB@klickit.com", "DELIVERY_PARTNER");
        UUID partnerAId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.ASSIGNED, partnerAId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Delivery partners are not authorized to cancel orders");
    }

    @Test
    @DisplayName("Delivery partner cannot cancel a PLACED order")
    void deliveryPartner_cannotCancelPlacedOrder_throwsAccessDeniedException() {
        authenticate("driverA@klickit.com", "DELIVERY_PARTNER");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.PLACED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Delivery partners are not authorized to cancel orders");
    }

    // =========================================================================
    // 6. Unauthorized Customer Attempting Another Customer's Cancellation
    // =========================================================================

    @Test
    @DisplayName("Unauthorized customer attempting another customer's cancellation is rejected")
    void unauthorizedCustomer_cannotCancelAnotherCustomerOrder_throwsAccessDeniedException() {
        authenticate("customerB@klickit.com", "CUSTOMER");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customerA@klickit.com", OrderStatus.PLACED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to access this order");
    }

    @Test
    @DisplayName("Anonymous user attempting cancellation is rejected")
    void anonymousUser_cannotCancelOrder_throwsAccessDeniedException() {
        SecurityContextHolder.clearContext();
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customerA@klickit.com", OrderStatus.PLACED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required to access this order");
    }

    // =========================================================================
    // 7. Valid Admin Cancellation Behavior
    // =========================================================================

    @Test
    @DisplayName("Admin can cancel any customer's PLACED order")
    void admin_canCancelPlacedOrder_success() {
        authenticate("admin@klickit.com", "ADMIN");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customerA@klickit.com", OrderStatus.PLACED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Admin can cancel any customer's ASSIGNED order")
    void admin_canCancelAssignedOrder_success() {
        authenticate("admin@klickit.com", "ADMIN");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customerA@klickit.com", OrderStatus.ASSIGNED, UUID.randomUUID());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Admin can cancel any customer's OUT_FOR_DELIVERY order")
    void admin_canCancelOutForDeliveryOrder_success() {
        authenticate("admin@klickit.com", "ADMIN");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customerA@klickit.com", OrderStatus.OUT_FOR_DELIVERY, UUID.randomUUID());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // =========================================================================
    // 8. Regression Coverage: Normal Delivery Flow Still Works
    // =========================================================================

    @Test
    @DisplayName("Normal delivery partner transitions (ASSIGNED -> OUT_FOR_DELIVERY -> DELIVERED) remain unaffected")
    void normalDeliveryWorkflow_regressionStillWorks() {
        String driverEmail = "driverA@klickit.com";
        authenticate(driverEmail, "DELIVERY_PARTNER");

        UUID partnerId = UUID.randomUUID();
        DeliveryPartner partner = DeliveryPartner.builder()
                .name("Driver A")
                .phone("9876543210")
                .build();
        partner.setId(partnerId);

        when(deliveryPartnerRepository.findByUserEmail(driverEmail)).thenReturn(Optional.of(partner));

        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customerA@klickit.com", OrderStatus.ASSIGNED, partnerId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Step 1: Start delivery (ASSIGNED -> OUT_FOR_DELIVERY)
        OrderResponse startRes = deliveryService.startDelivery(orderId);
        assertThat(startRes.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);

        // Step 2: Mark delivered (OUT_FOR_DELIVERY -> DELIVERED)
        OrderResponse deliverRes = deliveryService.markDelivered(orderId);
        assertThat(deliverRes.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }
}
