package com.klickit.order;

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
import com.klickit.order.entity.OrderItem;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.event.OrderCreatedEvent;
import com.klickit.order.repository.OrderRepository;
import com.klickit.order.service.OrderService;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderLifecycleIntegrationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private com.klickit.product.repository.ProductRepository productRepository;

    private OrderService orderService;
    private DeliveryService deliveryService;

    // Simulated in-memory storage for realistic state transitions across services
    private final Map<UUID, Order> orderDb = new HashMap<>();
    private final Map<UUID, DeliveryPartner> partnerDb = new HashMap<>();

    private DeliveryPartner partnerA;
    private DeliveryPartner partnerB;
    private DeliveryPartner nonPartnerUser;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, cartRepository, productRepository, deliveryPartnerRepository, eventPublisher);
        deliveryService = new DeliveryService(deliveryPartnerRepository, orderRepository, userRepository, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder());

        orderDb.clear();
        partnerDb.clear();

        // Setup Delivery Partner A (role = DELIVERY_PARTNER)
        UUID partnerAId = UUID.randomUUID();
        User userA = User.builder()
                .name("Delivery Partner A")
                .email("partnerA@klickit.com")
                .phone("9999900001")
                .role(Role.DELIVERY_PARTNER)
                .build();
        userA.setId(UUID.randomUUID());

        partnerA = DeliveryPartner.builder()
                .name("Delivery Partner A")
                .phone("9999900001")
                .user(userA)
                .build();
        partnerA.setId(partnerAId);
        partnerDb.put(partnerAId, partnerA);

        // Setup Delivery Partner B (role = DELIVERY_PARTNER)
        UUID partnerBId = UUID.randomUUID();
        User userB = User.builder()
                .name("Delivery Partner B")
                .email("partnerB@klickit.com")
                .phone("9999900002")
                .role(Role.DELIVERY_PARTNER)
                .build();
        userB.setId(UUID.randomUUID());

        partnerB = DeliveryPartner.builder()
                .name("Delivery Partner B")
                .phone("9999900002")
                .user(userB)
                .build();
        partnerB.setId(partnerBId);
        partnerDb.put(partnerBId, partnerB);

        // Setup Invalid Partner with CUSTOMER role
        UUID nonPartnerId = UUID.randomUUID();
        User customerUser = User.builder()
                .name("Customer Jay")
                .email("jay@customer.com")
                .phone("9999900003")
                .role(Role.CUSTOMER)
                .build();
        customerUser.setId(UUID.randomUUID());

        nonPartnerUser = DeliveryPartner.builder()
                .name("Customer Jay")
                .phone("9999900003")
                .user(customerUser)
                .build();
        nonPartnerUser.setId(nonPartnerId);
        partnerDb.put(nonPartnerId, nonPartnerUser);

        // Wire Mockito behaviors to in-memory state
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getId() == null) {
                o.setId(UUID.randomUUID());
            }
            orderDb.put(o.getId(), o);
            return o;
        });

        lenient().when(orderRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.ofNullable(orderDb.get(id));
        });

        lenient().when(orderRepository.findAllByOrderByCreatedAtDesc()).thenAnswer(invocation ->
                new ArrayList<>(orderDb.values()));

        lenient().when(deliveryPartnerRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.ofNullable(partnerDb.get(id));
        });

        lenient().when(deliveryPartnerRepository.findByUserEmail("partnerA@klickit.com"))
                .thenReturn(Optional.of(partnerA));
        lenient().when(deliveryPartnerRepository.findByUserEmail("partnerB@klickit.com"))
                .thenReturn(Optional.of(partnerB));

        lenient().when(orderRepository.findByDeliveryPartnerIdAndStatusInOrderByDeadlineAsc(any(UUID.class), any()))
                .thenAnswer(invocation -> {
                    UUID pid = invocation.getArgument(0);
                    List<OrderStatus> statuses = invocation.getArgument(1);
                    return orderDb.values().stream()
                            .filter(o -> pid.equals(o.getDeliveryPartnerId()) && statuses.contains(o.getStatus()))
                            .toList();
                });
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

    // =========================================================================
    // Complete End-to-End Lifecycle Flow
    // =========================================================================

    @Test
    @DisplayName("Complete Sprint 1 Admin Order Lifecycle: Create -> Admin views -> Assign Partner A -> Partner A sees & delivers -> Admin sees DELIVERED")
    void completeOrderLifecycleFlow() {
        // ---------------------------------------------------------------------
        // STEP 1: Customer creates order
        // ---------------------------------------------------------------------
        String sessionId = "customer-cart-session-1";
        Cart cart = Cart.builder()
                .sessionId(sessionId)
                .items(new ArrayList<>())
                .build();
        UUID maggiId = UUID.randomUUID();
        cart.addItem(CartItem.builder()
                .productId(maggiId)
                .productName("Maggi Noodles")
                .quantity(2)
                .unitPrice(new BigDecimal("14.00"))
                .build());
        com.klickit.product.entity.Product maggi = com.klickit.product.entity.Product.builder().name("Maggi Noodles").price(new BigDecimal("14.00")).active(true).build();
        maggi.setId(maggiId);
        when(productRepository.findById(maggiId)).thenReturn(Optional.of(maggi));
        
        UUID cokeId = UUID.randomUUID();
        cart.addItem(CartItem.builder()
                .productId(cokeId)
                .productName("Coca Cola")
                .quantity(1)
                .unitPrice(new BigDecimal("40.00"))
                .build());
        com.klickit.product.entity.Product coke = com.klickit.product.entity.Product.builder().name("Coca Cola").price(new BigDecimal("40.00")).active(true).build();
        coke.setId(cokeId);
        when(productRepository.findById(cokeId)).thenReturn(Optional.of(coke));

        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(cart));

        CheckoutRequest checkoutRequest = new CheckoutRequest(
                sessionId,
                "Student Jay",
                "9876543210",
                "Hostel 3, Room 102",
                Instant.now().plusSeconds(3600)
        );

        OrderResponse createdOrder = orderService.checkout(checkoutRequest);
        UUID orderId = createdOrder.getId();

        assertThat(createdOrder).isNotNull();
        assertThat(createdOrder.getId()).isEqualTo(orderId);
        assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(createdOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("93.00"));
        assertThat(createdOrder.getDeliveryFee()).isEqualByComparingTo(new BigDecimal("25.00"));

        // ---------------------------------------------------------------------
        // STEP 2 & 3: Admin authenticates and retrieves order
        // ---------------------------------------------------------------------
        authenticate("admin@klickit.com", "ADMIN");

        OrderResponse adminViewOrder = orderService.getOrderById(orderId);
        assertThat(adminViewOrder).isNotNull();
        assertThat(adminViewOrder.getId()).isEqualTo(orderId);
        assertThat(adminViewOrder.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(adminViewOrder.getCustomerName()).isEqualTo("Student Jay");

        List<OrderResponse> allAdminOrders = orderService.getAllOrders();
        assertThat(allAdminOrders).anyMatch(o -> o.getId().equals(orderId));

        // ---------------------------------------------------------------------
        // STEP 4: Admin assigns Delivery Partner A
        // ---------------------------------------------------------------------
        AssignDeliveryRequest assignRequest = new AssignDeliveryRequest(partnerA.getId());
        OrderResponse assignedOrder = orderService.assignDeliveryPartner(orderId, assignRequest);

        assertThat(assignedOrder).isNotNull();
        assertThat(assignedOrder.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(assignedOrder.getDeliveryPartnerId()).isEqualTo(partnerA.getId());
        assertThat(assignedOrder.getDeliveryPartnerName()).isEqualTo("Delivery Partner A");

        // ---------------------------------------------------------------------
        // STEP 5, 6 & 7: Partner A authenticates and retrieves assigned orders
        // ---------------------------------------------------------------------
        authenticate("partnerA@klickit.com", "DELIVERY_PARTNER");

        List<OrderResponse> partnerAOrders = deliveryService.getMyAssignedOrders();
        assertThat(partnerAOrders).hasSize(1);
        assertThat(partnerAOrders.get(0).getId()).isEqualTo(orderId);
        assertThat(partnerAOrders.get(0).getStatus()).isEqualTo(OrderStatus.ASSIGNED);

        // ---------------------------------------------------------------------
        // STEP 8: Partner A starts delivery (ASSIGNED -> OUT_FOR_DELIVERY)
        // ---------------------------------------------------------------------
        OrderResponse outForDeliveryOrder = deliveryService.startDelivery(orderId);
        assertThat(outForDeliveryOrder).isNotNull();
        assertThat(outForDeliveryOrder.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);

        // ---------------------------------------------------------------------
        // STEP 9: Partner A marks order as DELIVERED (OUT_FOR_DELIVERY -> DELIVERED)
        // ---------------------------------------------------------------------
        OrderResponse deliveredOrder = deliveryService.markDelivered(orderId);
        assertThat(deliveredOrder).isNotNull();
        assertThat(deliveredOrder.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        // ---------------------------------------------------------------------
        // STEP 9 & 10: Admin retrieves order and confirms status is DELIVERED
        // ---------------------------------------------------------------------
        authenticate("admin@klickit.com", "ADMIN");

        OrderResponse finalAdminOrder = orderService.getOrderById(orderId);
        assertThat(finalAdminOrder).isNotNull();
        assertThat(finalAdminOrder.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(finalAdminOrder.getDeliveryPartnerId()).isEqualTo(partnerA.getId());
    }

    // =========================================================================
    // Security & Invariant Tests
    // =========================================================================

    @Test
    @DisplayName("Partner B CANNOT access, view, or deliver Partner A's order")
    void partnerB_cannotAccessOrDeliverPartnerAOrder() {
        // Setup order assigned to Partner A
        Order order = Order.builder()
                .customerName("Customer A")
                .customerPhone("9876543210")
                .customerAddress("Block A")
                .totalAmount(new BigDecimal("100.00"))
                .status(OrderStatus.ASSIGNED)
                .deliveryPartnerId(partnerA.getId())
                .deliveryPartnerName(partnerA.getName())
                .deliveryPartnerPhone(partnerA.getPhone())
                .items(new ArrayList<>())
                .build();
        order.setId(UUID.randomUUID());
        orderDb.put(order.getId(), order);

        // Authenticate as Partner B
        authenticate("partnerB@klickit.com", "DELIVERY_PARTNER");

        // 1. Partner B's assigned orders list does NOT contain Partner A's order
        List<OrderResponse> partnerBOrders = deliveryService.getMyAssignedOrders();
        assertThat(partnerBOrders).noneMatch(o -> o.getId().equals(order.getId()));

        // 2. Partner B cannot manipulate path parameter to view Partner A's orders (throws 403)
        assertThatThrownBy(() -> deliveryService.getAssignedOrders(partnerA.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to access orders for another delivery partner");

        // 3. Partner B cannot start delivery or mark Partner A's order as delivered (throws 403)
        assertThatThrownBy(() -> deliveryService.startDelivery(order.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to deliver this order");

        assertThatThrownBy(() -> deliveryService.markDelivered(order.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to deliver this order");

        // 4. Partner B cannot inspect Partner A's order directly (throws 403)
        assertThatThrownBy(() -> orderService.getOrderById(order.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Customer CANNOT access admin endpoints or operations")
    void customer_cannotAccessAdminEndpoints() {
        authenticate("customer@test.com", "CUSTOMER");

        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Campus")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.PLACED)
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        // Customer cannot list all orders
        assertThatThrownBy(() -> orderService.getAllOrders())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators can perform this operation");

        // Customer cannot filter orders by status
        assertThatThrownBy(() -> orderService.getOrdersByStatus(OrderStatus.PLACED))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators can perform this operation");

        // Customer cannot update order status
        assertThatThrownBy(() -> orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.OUT_FOR_DELIVERY)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators can perform this operation");
    }

    @Test
    @DisplayName("Non-admin CANNOT assign delivery partners")
    void nonAdmin_cannotAssignDeliveryPartners() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Campus")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.PLACED)
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        AssignDeliveryRequest request = new AssignDeliveryRequest(partnerA.getId());

        // 1. Customer role fails
        authenticate("customer@test.com", "CUSTOMER");
        assertThatThrownBy(() -> orderService.assignDeliveryPartner(orderId, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators can perform this operation");

        // 2. Delivery partner role fails
        authenticate("partnerA@klickit.com", "DELIVERY_PARTNER");
        assertThatThrownBy(() -> orderService.assignDeliveryPartner(orderId, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators can perform this operation");

        // 3. Anonymous caller fails
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> orderService.assignDeliveryPartner(orderId, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    @DisplayName("Admin CANNOT assign a partner who lacks the DELIVERY_PARTNER role")
    void admin_cannotAssignNonDeliveryPartnerUser() {
        authenticate("admin@klickit.com", "ADMIN");

        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Campus")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.PLACED)
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        // Attempting to assign user with CUSTOMER role
        AssignDeliveryRequest request = new AssignDeliveryRequest(nonPartnerUser.getId());
        assertThatThrownBy(() -> orderService.assignDeliveryPartner(orderId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Assigned user must have the DELIVERY_PARTNER role");
    }

    @Test
    @DisplayName("Admin can update order status according to domain model")
    void admin_canUpdateOrderStatus() {
        authenticate("admin@klickit.com", "ADMIN");

        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Campus")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.ASSIGNED)
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        OrderResponse updated = orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.OUT_FOR_DELIVERY));
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);

        OrderResponse cancelled = orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.CANCELLED));
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // =========================================================================
    // Order State-Machine Enforcement Tests
    // =========================================================================

    @Test
    @DisplayName("Valid transitions succeed and persist new status: PLACED -> ASSIGNED -> OUT_FOR_DELIVERY -> DELIVERED")
    void validTransitions_succeedAndPersist() {
        authenticate("admin@klickit.com", "ADMIN");

        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer Valid")
                .customerPhone("1234567890")
                .customerAddress("Block V")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.PLACED)
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        // 1. PLACED -> ASSIGNED (via assignDeliveryPartner)
        AssignDeliveryRequest assignReq = new AssignDeliveryRequest(partnerA.getId());
        OrderResponse assigned = orderService.assignDeliveryPartner(orderId, assignReq);
        assertThat(assigned.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(orderDb.get(orderId).getDeliveryPartnerId()).isEqualTo(partnerA.getId());

        // 2. ASSIGNED -> OUT_FOR_DELIVERY (via updateStatus)
        OrderResponse outForDelivery = orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.OUT_FOR_DELIVERY));
        assertThat(outForDelivery.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);

        // 3. OUT_FOR_DELIVERY -> DELIVERED (via updateStatus)
        OrderResponse delivered = orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.DELIVERED));
        assertThat(delivered.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("Invalid transition PLACED -> DELIVERED is rejected and preserves PLACED status")
    void invalidTransition_placedToDelivered_rejectedAndStatePreserved() {
        authenticate("admin@klickit.com", "ADMIN");

        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.PLACED)
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        assertThatThrownBy(() -> orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.DELIVERED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from PLACED to DELIVERED");

        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    @DisplayName("Invalid transition ASSIGNED -> DELIVERED via admin updateStatus is rejected and preserves ASSIGNED status")
    void invalidTransition_assignedToDelivered_rejectedAndStatePreserved() {
        authenticate("admin@klickit.com", "ADMIN");

        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.ASSIGNED)
                .deliveryPartnerId(partnerA.getId())
                .deliveryPartnerName(partnerA.getName())
                .deliveryPartnerPhone(partnerA.getPhone())
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        assertThatThrownBy(() -> orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.DELIVERED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from ASSIGNED to DELIVERED");

        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(orderDb.get(orderId).getDeliveryPartnerId()).isEqualTo(partnerA.getId());
    }

    @Test
    @DisplayName("Invalid transition DELIVERED -> PLACED is rejected and preserves DELIVERED status")
    void invalidTransition_deliveredToPlaced_rejectedAndStatePreserved() {
        authenticate("admin@klickit.com", "ADMIN");

        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.DELIVERED)
                .deliveryPartnerId(partnerA.getId())
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        assertThatThrownBy(() -> orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.PLACED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from DELIVERED to PLACED");

        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("Invalid transition DELIVERED -> ASSIGNED is rejected via updateStatus and assignDeliveryPartner, preserving state")
    void invalidTransition_deliveredToAssigned_rejectedAndStatePreserved() {
        authenticate("admin@klickit.com", "ADMIN");

        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.DELIVERED)
                .deliveryPartnerId(partnerA.getId())
                .deliveryPartnerName(partnerA.getName())
                .deliveryPartnerPhone(partnerA.getPhone())
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        // 1. Rejected via updateStatus
        assertThatThrownBy(() -> orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.ASSIGNED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from DELIVERED to ASSIGNED");

        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(orderDb.get(orderId).getDeliveryPartnerId()).isEqualTo(partnerA.getId());

        // 2. Rejected via assignDeliveryPartner (attempt reassign to partner B)
        AssignDeliveryRequest reassignRequest = new AssignDeliveryRequest(partnerB.getId());
        assertThatThrownBy(() -> orderService.assignDeliveryPartner(orderId, reassignRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot assign delivery to a delivered order");

        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(orderDb.get(orderId).getDeliveryPartnerId()).isEqualTo(partnerA.getId());
        assertThat(orderDb.get(orderId).getDeliveryPartnerName()).isEqualTo(partnerA.getName());
    }

    @Test
    @DisplayName("Invalid transition CANCELLED -> ASSIGNED is rejected via updateStatus and assignDeliveryPartner, preserving state")
    void invalidTransition_cancelledToAssigned_rejectedAndStatePreserved() {
        authenticate("admin@klickit.com", "ADMIN");

        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.CANCELLED)
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        // 1. Rejected via updateStatus
        assertThatThrownBy(() -> orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.ASSIGNED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from CANCELLED to ASSIGNED");

        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderDb.get(orderId).getDeliveryPartnerId()).isNull();

        // 2. Rejected via assignDeliveryPartner
        AssignDeliveryRequest assignRequest = new AssignDeliveryRequest(partnerA.getId());
        assertThatThrownBy(() -> orderService.assignDeliveryPartner(orderId, assignRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot assign delivery to a cancelled order");

        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderDb.get(orderId).getDeliveryPartnerId()).isNull();
    }

    @Test
    @DisplayName("Invalid transition CANCELLED -> DELIVERED is rejected and preserves CANCELLED status")
    void invalidTransition_cancelledToDelivered_rejectedAndStatePreserved() {
        authenticate("admin@klickit.com", "ADMIN");

        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.CANCELLED)
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        assertThatThrownBy(() -> orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.DELIVERED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from CANCELLED to DELIVERED");

        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Invalid transition ASSIGNED -> PLACED is rejected and preserves ASSIGNED status")
    void invalidTransition_assignedToPlaced_rejectedAndStatePreserved() {
        authenticate("admin@klickit.com", "ADMIN");

        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.ASSIGNED)
                .deliveryPartnerId(partnerA.getId())
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        assertThatThrownBy(() -> orderService.updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.PLACED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from ASSIGNED to PLACED");

        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(orderDb.get(orderId).getDeliveryPartnerId()).isEqualTo(partnerA.getId());
    }

    // =========================================================================
    // Delivery Partner Lifecycle Tests (ASSIGNED -> OUT_FOR_DELIVERY -> DELIVERED)
    // =========================================================================

    @Test
    @DisplayName("Delivery partner can transition ASSIGNED -> OUT_FOR_DELIVERY")
    void driver_canStartDelivery_assignedToOutForDelivery() {
        authenticate("partnerA@klickit.com", "DELIVERY_PARTNER");

        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.ASSIGNED)
                .deliveryPartnerId(partnerA.getId())
                .deliveryPartnerName(partnerA.getName())
                .deliveryPartnerPhone(partnerA.getPhone())
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        OrderResponse response = deliveryService.startDelivery(orderId);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
    }

    @Test
    @DisplayName("Delivery partner can transition OUT_FOR_DELIVERY -> DELIVERED")
    void driver_canCompleteDelivery_outForDeliveryToDelivered() {
        authenticate("partnerA@klickit.com", "DELIVERY_PARTNER");

        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.OUT_FOR_DELIVERY)
                .deliveryPartnerId(partnerA.getId())
                .deliveryPartnerName(partnerA.getName())
                .deliveryPartnerPhone(partnerA.getPhone())
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        OrderResponse response = deliveryService.markDelivered(orderId);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("markDelivered rejects invalid states: PLACED, ASSIGNED, DELIVERED, CANCELLED")
    void markDelivered_rejectsInvalidStates_andPreservesOriginalStatus() {
        authenticate("partnerA@klickit.com", "DELIVERY_PARTNER");

        // 1. PLACED -> DELIVERED
        UUID orderPlacedId = UUID.randomUUID();
        Order orderPlaced = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.PLACED)
                .deliveryPartnerId(partnerA.getId())
                .items(new ArrayList<>())
                .build();
        orderPlaced.setId(orderPlacedId);
        orderDb.put(orderPlacedId, orderPlaced);

        assertThatThrownBy(() -> deliveryService.markDelivered(orderPlacedId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from PLACED to DELIVERED");
        assertThat(orderDb.get(orderPlacedId).getStatus()).isEqualTo(OrderStatus.PLACED);

        // 2. ASSIGNED -> DELIVERED
        UUID orderAssignedId = UUID.randomUUID();
        Order orderAssigned = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.ASSIGNED)
                .deliveryPartnerId(partnerA.getId())
                .items(new ArrayList<>())
                .build();
        orderAssigned.setId(orderAssignedId);
        orderDb.put(orderAssignedId, orderAssigned);

        assertThatThrownBy(() -> deliveryService.markDelivered(orderAssignedId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from ASSIGNED to DELIVERED");
        assertThat(orderDb.get(orderAssignedId).getStatus()).isEqualTo(OrderStatus.ASSIGNED);

        // 3. DELIVERED -> DELIVERED
        UUID orderDeliveredId = UUID.randomUUID();
        Order orderDelivered = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.DELIVERED)
                .deliveryPartnerId(partnerA.getId())
                .items(new ArrayList<>())
                .build();
        orderDelivered.setId(orderDeliveredId);
        orderDb.put(orderDeliveredId, orderDelivered);

        assertThatThrownBy(() -> deliveryService.markDelivered(orderDeliveredId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from DELIVERED to DELIVERED");
        assertThat(orderDb.get(orderDeliveredId).getStatus()).isEqualTo(OrderStatus.DELIVERED);

        // 4. CANCELLED -> DELIVERED
        UUID orderCancelledId = UUID.randomUUID();
        Order orderCancelled = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.CANCELLED)
                .deliveryPartnerId(partnerA.getId())
                .items(new ArrayList<>())
                .build();
        orderCancelled.setId(orderCancelledId);
        orderDb.put(orderCancelledId, orderCancelled);

        assertThatThrownBy(() -> deliveryService.markDelivered(orderCancelledId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from CANCELLED to DELIVERED");
        assertThat(orderDb.get(orderCancelledId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("startDelivery rejects invalid states: PLACED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED")
    void startDelivery_rejectsInvalidStates_andPreservesOriginalStatus() {
        authenticate("partnerA@klickit.com", "DELIVERY_PARTNER");

        // 1. PLACED -> OUT_FOR_DELIVERY
        UUID orderPlacedId = UUID.randomUUID();
        Order orderPlaced = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.PLACED)
                .deliveryPartnerId(partnerA.getId())
                .items(new ArrayList<>())
                .build();
        orderPlaced.setId(orderPlacedId);
        orderDb.put(orderPlacedId, orderPlaced);

        assertThatThrownBy(() -> deliveryService.startDelivery(orderPlacedId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from PLACED to OUT_FOR_DELIVERY");
        assertThat(orderDb.get(orderPlacedId).getStatus()).isEqualTo(OrderStatus.PLACED);

        // 2. OUT_FOR_DELIVERY -> OUT_FOR_DELIVERY
        UUID orderOutId = UUID.randomUUID();
        Order orderOut = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.OUT_FOR_DELIVERY)
                .deliveryPartnerId(partnerA.getId())
                .items(new ArrayList<>())
                .build();
        orderOut.setId(orderOutId);
        orderDb.put(orderOutId, orderOut);

        assertThatThrownBy(() -> deliveryService.startDelivery(orderOutId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from OUT_FOR_DELIVERY to OUT_FOR_DELIVERY");
        assertThat(orderDb.get(orderOutId).getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);

        // 3. DELIVERED -> OUT_FOR_DELIVERY
        UUID orderDeliveredId = UUID.randomUUID();
        Order orderDelivered = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.DELIVERED)
                .deliveryPartnerId(partnerA.getId())
                .items(new ArrayList<>())
                .build();
        orderDelivered.setId(orderDeliveredId);
        orderDb.put(orderDeliveredId, orderDelivered);

        assertThatThrownBy(() -> deliveryService.startDelivery(orderDeliveredId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from DELIVERED to OUT_FOR_DELIVERY");
        assertThat(orderDb.get(orderDeliveredId).getStatus()).isEqualTo(OrderStatus.DELIVERED);

        // 4. CANCELLED -> OUT_FOR_DELIVERY
        UUID orderCancelledId = UUID.randomUUID();
        Order orderCancelled = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.CANCELLED)
                .deliveryPartnerId(partnerA.getId())
                .items(new ArrayList<>())
                .build();
        orderCancelled.setId(orderCancelledId);
        orderDb.put(orderCancelledId, orderCancelled);

        assertThatThrownBy(() -> deliveryService.startDelivery(orderCancelledId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from CANCELLED to OUT_FOR_DELIVERY");
        assertThat(orderDb.get(orderCancelledId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Wrong driver cannot startDelivery or markDelivered on another driver's order")
    void wrongDriver_cannotStartOrCompleteDelivery() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.ASSIGNED)
                .deliveryPartnerId(partnerA.getId())
                .deliveryPartnerName(partnerA.getName())
                .deliveryPartnerPhone(partnerA.getPhone())
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        // Authenticate as Partner B
        authenticate("partnerB@klickit.com", "DELIVERY_PARTNER");

        // 1. Partner B cannot start delivery on Partner A's order
        assertThatThrownBy(() -> deliveryService.startDelivery(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to deliver this order");
        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.ASSIGNED);

        // Transition to OUT_FOR_DELIVERY legitimately by Partner A
        authenticate("partnerA@klickit.com", "DELIVERY_PARTNER");
        deliveryService.startDelivery(orderId);
        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);

        // 2. Partner B cannot mark delivered on Partner A's order
        authenticate("partnerB@klickit.com", "DELIVERY_PARTNER");
        assertThatThrownBy(() -> deliveryService.markDelivered(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to deliver this order");
        assertThat(orderDb.get(orderId).getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
    }

    @Test
    @DisplayName("Non-driver or anonymous caller cannot startDelivery or markDelivered")
    void nonDriverOrAnonymous_cannotStartOrCompleteDelivery() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .customerName("Customer")
                .customerPhone("1234567890")
                .customerAddress("Block A")
                .totalAmount(BigDecimal.TEN)
                .status(OrderStatus.ASSIGNED)
                .deliveryPartnerId(partnerA.getId())
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        orderDb.put(orderId, order);

        // 1. Customer role
        authenticate("customer@test.com", "CUSTOMER");
        assertThatThrownBy(() -> deliveryService.startDelivery(orderId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> deliveryService.markDelivered(orderId))
                .isInstanceOf(AccessDeniedException.class);

        // 2. Anonymous caller
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> deliveryService.startDelivery(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required for delivery operations");
        assertThatThrownBy(() -> deliveryService.markDelivered(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required for delivery operations");
    }
}
