package com.klickit.delivery;

import com.klickit.delivery.entity.DeliveryPartner;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.delivery.service.DeliveryService;
import com.klickit.order.dto.AssignDeliveryRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.repository.OrderRepository;
import com.klickit.order.service.OrderService;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceSecurityTest {

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks
    private DeliveryService deliveryService;

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
    @DisplayName("Partner A retrieves assigned orders: contains Order A, does NOT contain Order B")
    void partnerA_retrievesOnlyAssignedOrders() {
        // Setup Partner A identity and User link
        UUID userAId = UUID.randomUUID();
        User userA = User.builder()
                .name("Partner A")
                .email("partnerA@klickit.com")
                .phone("1111111111")
                .role(Role.DELIVERY_PARTNER)
                .build();
        userA.setId(userAId);

        UUID partnerAId = UUID.randomUUID();
        DeliveryPartner partnerA = DeliveryPartner.builder()
                .name("Partner A")
                .phone("1111111111")
                .user(userA)
                .build();
        partnerA.setId(partnerAId);

        // Setup Partner B
        UUID partnerBId = UUID.randomUUID();

        // Setup Order A and Order B
        Order orderA = createOrder(UUID.randomUUID(), partnerAId, "Order A");
        Order orderB = createOrder(UUID.randomUUID(), partnerBId, "Order B");

        // Authenticate as Partner A
        authenticate("partnerA@klickit.com", "DELIVERY_PARTNER");

        when(deliveryPartnerRepository.findByUserEmail("partnerA@klickit.com"))
                .thenReturn(Optional.of(partnerA));
        when(orderRepository.findByDeliveryPartnerIdAndStatusInOrderByDeadlineAsc(eq(partnerAId), any()))
                .thenReturn(List.of(orderA));

        List<OrderResponse> results = deliveryService.getMyAssignedOrders();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(orderA.getId());
        assertThat(results.get(0).getCustomerName()).isEqualTo("Order A");
        // Verify Order B is NOT included
        assertThat(results).noneMatch(o -> o.getId().equals(orderB.getId()));
    }

    @Test
    @DisplayName("Partner A attempts to manipulate partnerId path parameter to access Partner B's orders -> 403 AccessDenied")
    void partnerA_manipulatesPartnerIdPath_throwsAccessDenied() {
        UUID partnerAId = UUID.randomUUID();
        DeliveryPartner partnerA = DeliveryPartner.builder()
                .name("Partner A")
                .phone("1111111111")
                .build();
        partnerA.setId(partnerAId);

        UUID partnerBId = UUID.randomUUID();

        // Authenticate as Partner A
        authenticate("partnerA@klickit.com", "DELIVERY_PARTNER");

        when(deliveryPartnerRepository.findByUserEmail("partnerA@klickit.com"))
                .thenReturn(Optional.of(partnerA));

        // Partner A passes Partner B's UUID in the path parameter
        assertThatThrownBy(() -> deliveryService.getAssignedOrders(partnerBId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to access orders for another delivery partner");
    }

    @Test
    @DisplayName("Admin assigns Partner A: Partner A can see the order, Partner B cannot")
    void adminAssignsPartnerA_onlyPartnerACanSee() {
        UUID partnerAId = UUID.randomUUID();
        DeliveryPartner partnerA = DeliveryPartner.builder()
                .name("Partner A")
                .phone("1111111111")
                .build();
        partnerA.setId(partnerAId);

        UUID partnerBId = UUID.randomUUID();
        DeliveryPartner partnerB = DeliveryPartner.builder()
                .name("Partner B")
                .phone("2222222222")
                .build();
        partnerB.setId(partnerBId);

        // Order assigned to Partner A
        Order assignedOrder = createOrder(UUID.randomUUID(), partnerAId, "Assigned to A");

        // When Partner A checks their orders:
        authenticate("partnerA@klickit.com", "DELIVERY_PARTNER");
        when(deliveryPartnerRepository.findByUserEmail("partnerA@klickit.com")).thenReturn(Optional.of(partnerA));
        when(orderRepository.findByDeliveryPartnerIdAndStatusInOrderByDeadlineAsc(eq(partnerAId), any()))
                .thenReturn(List.of(assignedOrder));

        List<OrderResponse> partnerAOrders = deliveryService.getMyAssignedOrders();
        assertThat(partnerAOrders).hasSize(1);
        assertThat(partnerAOrders.get(0).getId()).isEqualTo(assignedOrder.getId());

        // When Partner B checks their orders:
        authenticate("partnerB@klickit.com", "DELIVERY_PARTNER");
        when(deliveryPartnerRepository.findByUserEmail("partnerB@klickit.com")).thenReturn(Optional.of(partnerB));
        when(orderRepository.findByDeliveryPartnerIdAndStatusInOrderByDeadlineAsc(eq(partnerBId), any()))
                .thenReturn(List.of()); // No orders assigned to B

        List<OrderResponse> partnerBOrders = deliveryService.getMyAssignedOrders();
        assertThat(partnerBOrders).isEmpty();
        assertThat(partnerBOrders).noneMatch(o -> o.getId().equals(assignedOrder.getId()));
    }

    @Test
    @DisplayName("Partner B cannot mark Partner A's order as delivered")
    void partnerB_cannotMarkPartnerAOrderDelivered() {
        UUID partnerAId = UUID.randomUUID();
        UUID partnerBId = UUID.randomUUID();
        DeliveryPartner partnerB = DeliveryPartner.builder()
                .name("Partner B")
                .phone("2222222222")
                .build();
        partnerB.setId(partnerBId);

        UUID orderId = UUID.randomUUID();
        Order orderA = createOrder(orderId, partnerAId, "Order for Partner A");

        authenticate("partnerB@klickit.com", "DELIVERY_PARTNER");
        when(deliveryPartnerRepository.findByUserEmail("partnerB@klickit.com")).thenReturn(Optional.of(partnerB));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(orderA));

        assertThatThrownBy(() -> deliveryService.markDelivered(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to deliver this order");
    }

    private Order createOrder(UUID id, UUID partnerId, String customerName) {
        Order order = Order.builder()
                .customerName(customerName)
                .customerPhone("9876543210")
                .customerAddress("Campus Block A")
                .customerEmail("customer@test.com")
                .totalAmount(new BigDecimal("100.00"))
                .status(OrderStatus.ASSIGNED)
                .deliveryPartnerId(partnerId)
                .items(new ArrayList<>())
                .build();
        order.setId(id);
        return order;
    }
}
