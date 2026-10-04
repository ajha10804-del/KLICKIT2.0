package com.klickit.order;

import com.klickit.cart.dto.AddToCartRequest;
import com.klickit.cart.entity.Cart;
import com.klickit.cart.entity.CartItem;
import com.klickit.cart.repository.CartRepository;
import com.klickit.delivery.entity.DeliveryPartner;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.delivery.service.DeliveryService;
import com.klickit.order.dto.AssignDeliveryRequest;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.dto.UpdateOrderStatusRequest;
import com.klickit.order.entity.Order;
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
public class OrderLifecycleEnhancementTest {

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
                .customerName("Customer A")
                .customerPhone("9876543210")
                .customerAddress("Room 101, Campus Hostel")
                .customerEmail(customerEmail)
                .totalAmount(new BigDecimal("120.00"))
                .deliveryFee(new BigDecimal("25.00"))
                .status(status)
                .deliveryPartnerId(partnerId)
                .items(new ArrayList<>())
                .build();
        order.setId(id);
        return order;
    }

    private DeliveryPartner createPartner(UUID partnerId, String email, String name, String phone) {
        User user = User.builder()
                .email(email)
                .role(Role.DELIVERY_PARTNER)
                .build();
        DeliveryPartner partner = DeliveryPartner.builder()
                .name(name)
                .phone(phone)
                .user(user)
                .build();
        partner.setId(partnerId);
        return partner;
    }

    // =========================================================================
    // 1. New checkout creates order with pending/PLACED status
    // =========================================================================

    @Test
    @DisplayName("Checkout creates an order with initial status PLACED (representing pending state)")
    void checkout_createsOrderWithPlacedStatus() {
        authenticate("customer@klickit.com", "CUSTOMER");

        UUID prodId = UUID.randomUUID();
        Product product = Product.builder()
                .name("Apples")
                .price(new BigDecimal("100.00"))
                .active(true)
                .build();
        product.setId(prodId);

        Cart cart = Cart.builder()
                .sessionId("sess_abc")
                .items(new ArrayList<>())
                .build();
        cart.addItem(CartItem.builder()
                .cart(cart)
                .productId(prodId)
                .productName("Apples")
                .unitPrice(new BigDecimal("100.00"))
                .quantity(1)
                .build());

        when(cartRepository.findBySessionId("sess_abc")).thenReturn(Optional.of(cart));
        when(productRepository.findById(prodId)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        CheckoutRequest request = new CheckoutRequest("sess_abc", "Customer", "9876543210", "Hostel 1", null);
        OrderResponse response = orderService.checkout(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PLACED);
    }

    // =========================================================================
    // 2. Admin Rejection (PLACED -> REJECTED)
    // =========================================================================

    @Test
    @DisplayName("Admin can reject a pending/PLACED order")
    void admin_canRejectPlacedOrder_success() {
        authenticate("admin@klickit.com", "ADMIN");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.PLACED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.rejectOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    @DisplayName("Admin can reject a pending order via updateStatus")
    void admin_canRejectPlacedOrder_viaUpdateStatus() {
        authenticate("admin@klickit.com", "ADMIN");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.PLACED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.REJECTED));

        assertThat(response.getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    @DisplayName("Admin cannot reject an order that is not PLACED (e.g., ASSIGNED)")
    void admin_cannotRejectNonPlacedOrder_throwsIllegalStateException() {
        authenticate("admin@klickit.com", "ADMIN");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.ASSIGNED, UUID.randomUUID());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.rejectOrder(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from ASSIGNED to REJECTED");
    }

    // =========================================================================
    // 3. REJECTED is a terminal state
    // =========================================================================

    @Test
    @DisplayName("Rejected order is terminal: cannot transition to any other state via updateStatus")
    void rejectedOrder_isTerminal_cannotTransition() {
        authenticate("admin@klickit.com", "ADMIN");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.REJECTED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        for (OrderStatus target : List.of(OrderStatus.PLACED, OrderStatus.ASSIGNED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, OrderStatus.CANCELLED)) {
            assertThatThrownBy(() -> orderService.updateStatus(orderId, new UpdateOrderStatusRequest(target)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition order from REJECTED to " + target);
        }
    }

    @Test
    @DisplayName("Rejected order cannot be assigned a delivery partner")
    void rejectedOrder_cannotBeAssigned_throwsIllegalStateException() {
        authenticate("admin@klickit.com", "ADMIN");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.REJECTED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        AssignDeliveryRequest req = new AssignDeliveryRequest(UUID.randomUUID());
        assertThatThrownBy(() -> orderService.assignDeliveryPartner(orderId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot assign delivery to a rejected order");
    }

    @Test
    @DisplayName("Rejected order cannot be started by delivery driver")
    void rejectedOrder_cannotBeStarted_throwsIllegalStateException() {
        String driverEmail = "driverA@klickit.com";
        authenticate(driverEmail, "DELIVERY_PARTNER");

        UUID partnerId = UUID.randomUUID();
        DeliveryPartner partner = createPartner(partnerId, driverEmail, "Driver A", "9876543210");
        when(deliveryPartnerRepository.findByUserEmail(driverEmail)).thenReturn(Optional.of(partner));

        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.REJECTED, partnerId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> deliveryService.startDelivery(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from REJECTED to OUT_FOR_DELIVERY");
    }

    @Test
    @DisplayName("Rejected order cannot be delivered by delivery driver")
    void rejectedOrder_cannotBeDelivered_throwsIllegalStateException() {
        String driverEmail = "driverA@klickit.com";
        authenticate(driverEmail, "DELIVERY_PARTNER");

        UUID partnerId = UUID.randomUUID();
        DeliveryPartner partner = createPartner(partnerId, driverEmail, "Driver A", "9876543210");
        when(deliveryPartnerRepository.findByUserEmail(driverEmail)).thenReturn(Optional.of(partner));

        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.REJECTED, partnerId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> deliveryService.markDelivered(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from REJECTED to DELIVERED");
    }

    @Test
    @DisplayName("Rejected order cannot be cancelled")
    void rejectedOrder_cannotBeCancelled_throwsIllegalStateException() {
        authenticate("admin@klickit.com", "ADMIN");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.REJECTED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel a rejected order");
    }

    // =========================================================================
    // 4. Atomic Admin Approval + Driver Assignment
    // =========================================================================

    @Test
    @DisplayName("Admin can approve and assign valid driver to PLACED order atomically")
    void admin_canApproveAndAssignDriver_success() {
        authenticate("admin@klickit.com", "ADMIN");
        UUID partnerId = UUID.randomUUID();
        DeliveryPartner partner = createPartner(partnerId, "driverA@klickit.com", "Driver A", "9876543210");

        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.PLACED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findById(partnerId)).thenReturn(Optional.of(partner));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        AssignDeliveryRequest req = new AssignDeliveryRequest(partnerId);
        OrderResponse response = orderService.assignDeliveryPartner(orderId, req);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(order.getDeliveryPartnerId()).isEqualTo(partnerId);
        assertThat(order.getDeliveryPartnerName()).isEqualTo("Driver A");
        assertThat(order.getDeliveryPartnerPhone()).isEqualTo("9876543210");
    }

    @Test
    @DisplayName("Assignment cannot happen without a valid delivery partner (missing ID throws exception)")
    void assignment_failsWithoutValidDriver_throwsException() {
        authenticate("admin@klickit.com", "ADMIN");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.PLACED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        AssignDeliveryRequest req = new AssignDeliveryRequest(null);
        assertThatThrownBy(() -> orderService.assignDeliveryPartner(orderId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Delivery partner ID is required");
    }

    @Test
    @DisplayName("updateStatus to ASSIGNED without an assigned delivery partner is rejected")
    void updateStatus_toAssignedWithoutDriver_throwsIllegalStateException() {
        authenticate("admin@klickit.com", "ADMIN");
        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.PLACED, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.ASSIGNED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order to ASSIGNED without an assigned delivery partner");
    }

    // =========================================================================
    // 5. Authorization barriers for Approval / Rejection / Assignment
    // =========================================================================

    @Test
    @DisplayName("Customer cannot approve/assign or reject orders")
    void customer_cannotApproveOrRejectOrders_throwsAccessDeniedException() {
        authenticate("customer@klickit.com", "CUSTOMER");
        UUID orderId = UUID.randomUUID();

        AssignDeliveryRequest assignReq = new AssignDeliveryRequest(UUID.randomUUID());
        assertThatThrownBy(() -> orderService.assignDeliveryPartner(orderId, assignReq))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> orderService.rejectOrder(orderId))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.REJECTED)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Delivery partner cannot approve/assign or reject orders")
    void deliveryPartner_cannotApproveOrRejectOrders_throwsAccessDeniedException() {
        authenticate("driver@klickit.com", "DELIVERY_PARTNER");
        UUID orderId = UUID.randomUUID();

        AssignDeliveryRequest assignReq = new AssignDeliveryRequest(UUID.randomUUID());
        assertThatThrownBy(() -> orderService.assignDeliveryPartner(orderId, assignReq))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> orderService.rejectOrder(orderId))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.REJECTED)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // =========================================================================
    // 6. Driver workflow regression & wrong driver protection
    // =========================================================================

    @Test
    @DisplayName("Assigned driver can start and complete delivery; wrong driver is rejected")
    void driver_startAndDeliverWorkflow_withWrongDriverProtection() {
        String driverAEmail = "driverA@klickit.com";
        String driverBEmail = "driverB@klickit.com";

        UUID partnerAId = UUID.randomUUID();
        DeliveryPartner partnerA = createPartner(partnerAId, driverAEmail, "Driver A", "1111111111");

        UUID partnerBId = UUID.randomUUID();
        DeliveryPartner partnerB = createPartner(partnerBId, driverBEmail, "Driver B", "2222222222");

        UUID orderId = UUID.randomUUID();
        Order order = createOrder(orderId, "customer@klickit.com", OrderStatus.ASSIGNED, partnerAId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findByUserEmail(driverAEmail)).thenReturn(Optional.of(partnerA));
        when(deliveryPartnerRepository.findByUserEmail(driverBEmail)).thenReturn(Optional.of(partnerB));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Wrong driver B cannot start delivery
        authenticate(driverBEmail, "DELIVERY_PARTNER");
        assertThatThrownBy(() -> deliveryService.startDelivery(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to deliver this order");

        // 2. Assigned driver A starts delivery
        authenticate(driverAEmail, "DELIVERY_PARTNER");
        OrderResponse startRes = deliveryService.startDelivery(orderId);
        assertThat(startRes.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);

        // 3. Wrong driver B cannot complete delivery
        authenticate(driverBEmail, "DELIVERY_PARTNER");
        assertThatThrownBy(() -> deliveryService.markDelivered(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to deliver this order");

        // 4. Assigned driver A completes delivery
        authenticate(driverAEmail, "DELIVERY_PARTNER");
        OrderResponse deliverRes = deliveryService.markDelivered(orderId);
        assertThat(deliverRes.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        // 5. Delivered order cannot be restarted or redelivered
        assertThatThrownBy(() -> deliveryService.startDelivery(orderId))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> deliveryService.markDelivered(orderId))
                .isInstanceOf(IllegalStateException.class);
    }
}
