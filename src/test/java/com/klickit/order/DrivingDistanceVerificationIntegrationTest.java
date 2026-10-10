package com.klickit.order;

import com.klickit.cart.entity.Cart;
import com.klickit.cart.entity.CartItem;
import com.klickit.cart.repository.CartRepository;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.dto.UpdateOrderStatusRequest;
import com.klickit.order.entity.DrivingDistanceStatus;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.repository.OrderRepository;
import com.klickit.order.routing.DrivingDistanceResult;
import com.klickit.order.routing.DrivingDistanceService;
import com.klickit.order.service.DeliveryLocationProperties;
import com.klickit.order.service.OrderService;
import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrivingDistanceVerificationIntegrationTest {

    private static final double STORE_LAT = 23.073427650432762;
    private static final double STORE_LON = 76.82864818972119;
    private static final double GATE_LAT = 23.075611;
    private static final double GATE_LON = 76.850082;
    private static final double CUSTOMER_LAT = 23.078900;
    private static final double CUSTOMER_LON = 76.835000;

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
    private DrivingDistanceService drivingDistanceService;

    private OrderService orderService;
    private DeliveryLocationProperties deliveryLocationProperties;

    @BeforeEach
    void setUp() {
        deliveryLocationProperties = new DeliveryLocationProperties(
                STORE_LAT, STORE_LON, 6.0, GATE_LAT, GATE_LON, 1100.0, true);

        orderService = new OrderService(
                orderRepository,
                cartRepository,
                productRepository,
                deliveryPartnerRepository,
                eventPublisher,
                Clock.systemUTC(),
                new BigDecimal("25.00"),
                new BigDecimal("199.00"),
                deliveryLocationProperties,
                drivingDistanceService
        );

        authenticate("admin@klickit.test", "ADMIN");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String email, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Order createTestOrder(UUID orderId, int distanceMeters, DrivingDistanceStatus status, boolean meetAtGate) {
        double destLat = meetAtGate ? GATE_LAT : CUSTOMER_LAT;
        double destLng = meetAtGate ? GATE_LON : CUSTOMER_LON;

        Order order = Order.builder()
                .customerName("Alice Student")
                .customerPhone("9876543210")
                .customerAddress("Block 1, Room 101")
                .customerLatitude(CUSTOMER_LAT)
                .customerLongitude(CUSTOMER_LON)
                .meetAtGate(meetAtGate)
                .totalAmount(new BigDecimal("150.00"))
                .status(OrderStatus.PLACED)
                .drivingDistanceMeters(distanceMeters)
                .drivingDistanceStatus(status)
                .drivingDistanceDestinationLat(destLat)
                .drivingDistanceDestinationLng(destLng)
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        return order;
    }

    @Test
    @DisplayName("1. Verified route at 4,900 metres: acceptance succeeds via approveOrder")
    void verifiedRoute_at4900m_acceptanceSucceeds() {
        UUID orderId = UUID.randomUUID();
        Order order = createTestOrder(orderId, 4900, DrivingDistanceStatus.ELIGIBLE, false);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.approveOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.READY_TO_ASSIGN);
        assertThat(response.getDrivingDistanceMeters()).isEqualTo(4900);
        assertThat(response.getDrivingDistanceEligible()).isTrue();
        assertThat(response.getDrivingDistanceStatus()).isEqualTo(DrivingDistanceStatus.ELIGIBLE);
        assertThat(response.getDrivingDistanceKm()).isEqualByComparingTo(new BigDecimal("4.90"));
    }

    @Test
    @DisplayName("1b. Verified route at 4,999 metres: acceptance succeeds via approveOrder")
    void verifiedRoute_at4999m_acceptanceSucceeds() {
        UUID orderId = UUID.randomUUID();
        Order order = createTestOrder(orderId, 4999, DrivingDistanceStatus.ELIGIBLE, false);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.approveOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.READY_TO_ASSIGN);
        assertThat(response.getDrivingDistanceMeters()).isEqualTo(4999);
        assertThat(response.getDrivingDistanceEligible()).isTrue();
        assertThat(response.getDrivingDistanceStatus()).isEqualTo(DrivingDistanceStatus.ELIGIBLE);
        assertThat(response.getDrivingDistanceKm()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    @DisplayName("2. Verified route at exactly 5,000 metres: acceptance succeeds via approveOrder")
    void verifiedRoute_atExactly5000m_acceptanceSucceeds() {
        UUID orderId = UUID.randomUUID();
        Order order = createTestOrder(orderId, 5000, DrivingDistanceStatus.ELIGIBLE, false);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.approveOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.READY_TO_ASSIGN);
        assertThat(response.getDrivingDistanceMeters()).isEqualTo(5000);
        assertThat(response.getDrivingDistanceEligible()).isTrue();
        assertThat(response.getDrivingDistanceKm()).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    @DisplayName("3. Verified route at 5,001 metres: acceptance fails and order remains PLACED")
    void verifiedRoute_at5001m_acceptanceFails() {
        UUID orderId = UUID.randomUUID();
        Order order = createTestOrder(orderId, 5001, DrivingDistanceStatus.EXCEEDED, false);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.approveOrder(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verified road driving distance (5001 m) exceeds the 5,000 metre limit");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        verify(orderRepository, never()).save(order);
    }

    @Test
    @DisplayName("4. Display rounding to 5.00 km does not make a route above 5,000 metres eligible")
    void displayRounding_5001m_isNotEligible() {
        UUID orderId = UUID.randomUUID();
        Order order = createTestOrder(orderId, 5001, DrivingDistanceStatus.EXCEEDED, false);

        OrderResponse response = OrderResponse.from(order);

        // 5001 metres rounds down to 5.00 km at 2 decimal places, but eligible MUST be false
        assertThat(response.getDrivingDistanceKm()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(response.getDrivingDistanceMeters()).isEqualTo(5001);
        assertThat(response.getDrivingDistanceEligible()).isFalse();
        assertThat(response.getDrivingDistanceStatus()).isEqualTo(DrivingDistanceStatus.EXCEEDED);
    }

    @Test
    @DisplayName("5. Both admin acceptance endpoints enforce the same 5 km rule (updateStatus path)")
    void updateStatus_enforcesSameRule() {
        UUID orderId = UUID.randomUUID();
        Order order = createTestOrder(orderId, 5200, DrivingDistanceStatus.EXCEEDED, false);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(OrderStatus.READY_TO_ASSIGN);
        assertThatThrownBy(() -> orderService.updateStatus(orderId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds the 5,000 metre limit");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    @DisplayName("6. Campus-gate orders resolve destination as configured gate coordinates")
    void campusGateOrders_useGateCoordinates() {
        UUID orderId = UUID.randomUUID();
        // Destination is campus gate
        Order order = createTestOrder(orderId, 3200, DrivingDistanceStatus.ELIGIBLE, true);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.approveOrder(orderId);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.READY_TO_ASSIGN);
        assertThat(order.getDrivingDistanceDestinationLat()).isEqualTo(GATE_LAT);
        assertThat(order.getDrivingDistanceDestinationLng()).isEqualTo(GATE_LON);
    }

    @Test
    @DisplayName("7. Customer orders resolve destination as customer delivery coordinates")
    void customerOrders_useCustomerCoordinates() {
        UUID orderId = UUID.randomUUID();
        Order order = createTestOrder(orderId, 4200, DrivingDistanceStatus.ELIGIBLE, false);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.approveOrder(orderId);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.READY_TO_ASSIGN);
        assertThat(order.getDrivingDistanceDestinationLat()).isEqualTo(CUSTOMER_LAT);
        assertThat(order.getDrivingDistanceDestinationLng()).isEqualTo(CUSTOMER_LON);
    }

    @Test
    @DisplayName("8. Unavailable route blocks acceptance with clear retry message")
    void unavailableRoute_blocksAcceptance() {
        UUID orderId = UUID.randomUUID();
        Order order = createTestOrder(orderId, 0, DrivingDistanceStatus.UNAVAILABLE, false);
        order.setDrivingDistanceMeters(null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(drivingDistanceService.calculateDistance(STORE_LAT, STORE_LON, CUSTOMER_LAT, CUSTOMER_LON))
                .thenReturn(DrivingDistanceResult.unavailable("Network timeout"));

        assertThatThrownBy(() -> orderService.approveOrder(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("road driving distance could not be verified. Please retry.");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    @DisplayName("9. An unavailable or exceeded route does not block a valid admin rejection")
    void unavailableOrExceededRoute_allowsAdminRejection() {
        UUID orderId = UUID.randomUUID();
        Order order = createTestOrder(orderId, 6500, DrivingDistanceStatus.EXCEEDED, false);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.rejectOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    @DisplayName("10. An unavailable route can be recalculated and accepted after successful verification")
    void unavailableRoute_recalculatedAndAcceptedOnApproval() {
        UUID orderId = UUID.randomUUID();
        Order order = createTestOrder(orderId, 0, DrivingDistanceStatus.UNAVAILABLE, false);
        order.setDrivingDistanceMeters(null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        // On-demand recalculation succeeds with 4,300 metres
        when(drivingDistanceService.calculateDistance(STORE_LAT, STORE_LON, CUSTOMER_LAT, CUSTOMER_LON))
                .thenReturn(DrivingDistanceResult.eligible(4300));

        OrderResponse response = orderService.approveOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.READY_TO_ASSIGN);
        assertThat(response.getDrivingDistanceMeters()).isEqualTo(4300);
        assertThat(response.getDrivingDistanceEligible()).isTrue();
        assertThat(order.getDrivingDistanceStatus()).isEqualTo(DrivingDistanceStatus.ELIGIBLE);
    }

    @Test
    @DisplayName("11. A result calculated for a different destination cannot authorize acceptance")
    void differentDestination_triggersRecalculationOrFails() {
        UUID orderId = UUID.randomUUID();
        Order order = createTestOrder(orderId, 2500, DrivingDistanceStatus.ELIGIBLE, false);
        // Stored destination coordinates differ from current order destination
        order.setDrivingDistanceDestinationLat(12.345678);
        order.setDrivingDistanceDestinationLng(98.765432);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        // On-demand recalculation for the actual destination returns EXCEEDED
        when(drivingDistanceService.calculateDistance(STORE_LAT, STORE_LON, CUSTOMER_LAT, CUSTOMER_LON))
                .thenReturn(DrivingDistanceResult.exceeded(5400));

        assertThatThrownBy(() -> orderService.approveOrder(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds the 5,000 metre limit");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    @DisplayName("12. Repeated admin approve requests cannot transition an already approved order")
    void repeatedApproval_cannotBypassStateTransitions() {
        UUID orderId = UUID.randomUUID();
        Order order = createTestOrder(orderId, 4500, DrivingDistanceStatus.ELIGIBLE, false);
        order.setStatus(OrderStatus.READY_TO_ASSIGN);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.approveOrder(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order from READY_TO_ASSIGN to READY_TO_ASSIGN");
    }

    @Test
    @DisplayName("13. Customers and unauthorized roles cannot call admin acceptance endpoints")
    void unauthorizedRoles_cannotApproveOrder() {
        UUID orderId = UUID.randomUUID();

        authenticate("student@klickit.test", "CUSTOMER");
        assertThatThrownBy(() -> orderService.approveOrder(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators can perform this operation");

        authenticate("driver@klickit.test", "DELIVERY_PARTNER");
        assertThatThrownBy(() -> orderService.approveOrder(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators can perform this operation");
    }
}
