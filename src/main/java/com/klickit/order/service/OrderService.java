package com.klickit.order.service;

import com.klickit.cart.entity.Cart;
import com.klickit.cart.entity.CartItem;
import com.klickit.cart.repository.CartRepository;
import com.klickit.common.exception.ResourceNotFoundException;
import com.klickit.delivery.entity.DeliveryPartner;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.order.dto.AssignDeliveryRequest;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.dto.UpdateOrderStatusRequest;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderItem;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.event.OrderCreatedEvent;
import com.klickit.order.repository.OrderRepository;
import com.klickit.user.entity.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PLACED, Set.of(OrderStatus.ASSIGNED, OrderStatus.CANCELLED),
            OrderStatus.ASSIGNED, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED),
            OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.CANCELLED, Set.of()
    );

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final BigDecimal defaultDeliveryFee;
    private final BigDecimal freeDeliveryThreshold;

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            DeliveryPartnerRepository deliveryPartnerRepository,
            ApplicationEventPublisher eventPublisher) {
        this(orderRepository, cartRepository, deliveryPartnerRepository, eventPublisher, Clock.systemUTC(), new BigDecimal("25.00"), new BigDecimal("199.00"));
    }

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            DeliveryPartnerRepository deliveryPartnerRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this(orderRepository, cartRepository, deliveryPartnerRepository, eventPublisher, clock, new BigDecimal("25.00"), new BigDecimal("199.00"));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            DeliveryPartnerRepository deliveryPartnerRepository,
            ApplicationEventPublisher eventPublisher,
            @org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock,
            @Value("${klickit.delivery.fee:25.00}") BigDecimal defaultDeliveryFee,
            @Value("${klickit.delivery.free-threshold:199.00}") BigDecimal freeDeliveryThreshold) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.deliveryPartnerRepository = deliveryPartnerRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.defaultDeliveryFee = defaultDeliveryFee != null ? defaultDeliveryFee : new BigDecimal("25.00");
        this.freeDeliveryThreshold = freeDeliveryThreshold != null ? freeDeliveryThreshold : new BigDecimal("199.00");
    }

    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        Cart cart = cartRepository.findBySessionId(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "sessionId", request.getSessionId()));

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty cart");
        }

        // Authoritative 15-minute fulfillment SLA calculated server-side; client value is untrusted
        Instant authoritativeDeadline = Instant.now(clock).plus(15, ChronoUnit.MINUTES);

        Order order = Order.builder()
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .customerAddress(request.getCustomerAddress())
                .customerEmail(getCurrentUserEmail())
                .deadline(authoritativeDeadline)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            BigDecimal lineTotal = cartItem.getUnitPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            OrderItem orderItem = OrderItem.builder()
                    .productName(cartItem.getProductName())
                    .quantity(cartItem.getQuantity())
                    .price(cartItem.getUnitPrice())
                    .build();
            order.addItem(orderItem);
        }

        BigDecimal fee = subtotal.compareTo(freeDeliveryThreshold) < 0
                ? this.defaultDeliveryFee
                : BigDecimal.ZERO;

        order.setDeliveryFee(fee);
        order.setTotalAmount(subtotal.add(fee));
        Order saved = orderRepository.save(order);

        cart.getItems().clear();
        cartRepository.save(cart);

        eventPublisher.publishEvent(new OrderCreatedEvent(saved));

        return OrderResponse.from(saved);
    }

    public OrderResponse getOrderById(UUID id) {
        Order order = findOrderOrThrow(id);
        validateOrderAccess(order);
        return OrderResponse.from(order);
    }

    public List<OrderResponse> getCustomerOrders() {
        String email = getCurrentUserEmail();
        if (email == null) {
            throw new AccessDeniedException("Authentication required to view your orders");
        }
        return orderRepository.findByCustomerEmailOrderByCreatedAtDesc(email).stream()
                .map(OrderResponse::from)
                .toList();
    }

    public List<OrderResponse> getAllOrders() {
        validateAdminAccess();
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(OrderResponse::from)
                .toList();
    }

    public List<OrderResponse> getOrdersByStatus(OrderStatus status) {
        validateAdminAccess();
        return orderRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional
    public OrderResponse updateStatus(UUID id, UpdateOrderStatusRequest request) {
        validateAdminAccess();
        Order order = findOrderOrThrow(id);

        OrderStatus currentStatus = order.getStatus();
        OrderStatus newStatus = request.getStatus();

        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (newStatus == null || !allowed.contains(newStatus)) {
            throw new IllegalStateException(
                    String.format("Cannot transition order from %s to %s", currentStatus, newStatus));
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.save(order);
        return OrderResponse.from(updated);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID id) {
        Order order = findOrderOrThrow(id);
        validateOrderAccess(order);
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel a delivered order");
        }
        order.setStatus(OrderStatus.CANCELLED);
        Order updated = orderRepository.save(order);
        return OrderResponse.from(updated);
    }

    @Transactional
    public OrderResponse assignDeliveryPartner(UUID id, AssignDeliveryRequest request) {
        validateAdminAccess();

        Order order = findOrderOrThrow(id);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot assign delivery to a cancelled order");
        }
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot assign delivery to a delivered order");
        }

        DeliveryPartner partner = deliveryPartnerRepository.findById(request.getDeliveryPartnerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DeliveryPartner", "id", request.getDeliveryPartnerId()));

        if (partner.getUser() != null && partner.getUser().getRole() != Role.DELIVERY_PARTNER) {
            throw new IllegalArgumentException("Assigned user must have the DELIVERY_PARTNER role");
        }

        order.setDeliveryPartnerId(partner.getId());
        order.setDeliveryPartnerName(partner.getName());
        order.setDeliveryPartnerPhone(partner.getPhone());
        order.setStatus(OrderStatus.ASSIGNED);
        Order updated = orderRepository.save(order);
        return OrderResponse.from(updated);
    }

    private void validateOrderAccess(Order order) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Authentication required to access this order");
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return; // Admin has universal order access
        }

        boolean isDeliveryPartner = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DELIVERY_PARTNER"));
        if (isDeliveryPartner) {
            DeliveryPartner partner = deliveryPartnerRepository.findByUserEmail(auth.getName()).orElse(null);
            if (partner != null && order.getDeliveryPartnerId() != null
                    && order.getDeliveryPartnerId().equals(partner.getId())) {
                return; // Assigned delivery partner has access
            }
            throw new AccessDeniedException("You are not authorized to access this order");
        }

        // Customer access: order must belong to authenticated customer
        String currentUserEmail = auth.getName();
        if (order.getCustomerEmail() == null || !order.getCustomerEmail().equalsIgnoreCase(currentUserEmail)) {
            throw new AccessDeniedException("You are not authorized to access this order");
        }
    }

    private void validateAdminAccess() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Authentication required for administrative operations");
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new AccessDeniedException("Only administrators can perform this operation");
        }
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateNotificationStatus(UUID orderId, boolean sent) {
        orderRepository.updateNotificationStatus(orderId, sent);
    }

    private Order findOrderOrThrow(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
    }
}
