package com.klickit.order.service;

import com.klickit.cart.entity.Cart;
import com.klickit.cart.entity.CartItem;
import com.klickit.cart.repository.CartRepository;
import com.klickit.common.exception.ResourceNotFoundException;
import com.klickit.delivery.entity.DeliveryPartner;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.order.dto.AssignDeliveryRequest;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.dto.LocationUpdateRequest;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PLACED, Set.of(OrderStatus.READY_TO_ASSIGN, OrderStatus.REJECTED, OrderStatus.CANCELLED),
            OrderStatus.READY_TO_ASSIGN, Set.of(OrderStatus.ASSIGNED, OrderStatus.CANCELLED),
            OrderStatus.ASSIGNED, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED),
            OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
            OrderStatus.REJECTED, Set.of(),
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.CANCELLED, Set.of()
    );

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final com.klickit.product.repository.ProductRepository productRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final BigDecimal defaultDeliveryFee;
    private final BigDecimal freeDeliveryThreshold;
    private final Double storeLatitude;
    private final Double storeLongitude;
    private final double maxDeliveryRadiusKm;

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            com.klickit.product.repository.ProductRepository productRepository,
            DeliveryPartnerRepository deliveryPartnerRepository,
            ApplicationEventPublisher eventPublisher) {
        this(orderRepository, cartRepository, productRepository, deliveryPartnerRepository, eventPublisher, Clock.systemUTC(), new BigDecimal("25.00"), new BigDecimal("199.00"));
    }

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            com.klickit.product.repository.ProductRepository productRepository,
            DeliveryPartnerRepository deliveryPartnerRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this(orderRepository, cartRepository, productRepository, deliveryPartnerRepository, eventPublisher, clock, new BigDecimal("25.00"), new BigDecimal("199.00"));
    }

    /**
     * Existing constructor retained for current tests and direct service construction.
     * Delivery-radius enforcement is disabled for this compatibility constructor because
     * no store coordinates are supplied. Spring production wiring uses the constructor below.
     */
    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            com.klickit.product.repository.ProductRepository productRepository,
            DeliveryPartnerRepository deliveryPartnerRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            BigDecimal defaultDeliveryFee,
            BigDecimal freeDeliveryThreshold) {
        this(
                orderRepository,
                cartRepository,
                productRepository,
                deliveryPartnerRepository,
                eventPublisher,
                clock,
                defaultDeliveryFee,
                freeDeliveryThreshold,
                "",
                "",
                5.0
        );
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            com.klickit.product.repository.ProductRepository productRepository,
            DeliveryPartnerRepository deliveryPartnerRepository,
            ApplicationEventPublisher eventPublisher,
            @org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock,
            @Value("${klickit.delivery.fee:25.00}") BigDecimal defaultDeliveryFee,
            @Value("${klickit.delivery.free-threshold:199.00}") BigDecimal freeDeliveryThreshold,
            @Value("${klickit.delivery.store-latitude:}") String storeLatitude,
            @Value("${klickit.delivery.store-longitude:}") String storeLongitude,
            @Value("${klickit.delivery.max-radius-km:5}") double maxDeliveryRadiusKm) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.deliveryPartnerRepository = deliveryPartnerRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.defaultDeliveryFee = defaultDeliveryFee != null ? defaultDeliveryFee : new BigDecimal("25.00");
        this.freeDeliveryThreshold = freeDeliveryThreshold != null ? freeDeliveryThreshold : new BigDecimal("199.00");
        this.storeLatitude = parseCoordinate(storeLatitude, -90.0, 90.0);
        this.storeLongitude = parseCoordinate(storeLongitude, -180.0, 180.0);
        this.maxDeliveryRadiusKm = maxDeliveryRadiusKm > 0 ? maxDeliveryRadiusKm : 5.0;
    }

    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        Cart cart = cartRepository.findBySessionId(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "sessionId", request.getSessionId()));

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty cart");
        }

        // Never trust the browser alone: enforce the configured delivery radius again on the server.
        validateDeliveryRadius(request);

        // Authoritative 15-minute fulfillment SLA calculated server-side; client value is untrusted
        Instant authoritativeDeadline = Instant.now(clock).plus(15, ChronoUnit.MINUTES);

        Order order = Order.builder()
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .customerAddress(request.getCustomerAddress())
                .customerLatitude(request.getCustomerLatitude())
                .customerLongitude(request.getCustomerLongitude())
                .customerEmail(getCurrentUserEmail())
                .deadline(authoritativeDeadline)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            com.klickit.product.entity.Product product = productRepository.findById(cartItem.getProductId())
                    .orElseThrow(() -> new IllegalStateException("Product not found: " + cartItem.getProductName()));
            
            if (!product.isActive()) {
                throw new IllegalStateException("Product is no longer available: " + product.getName());
            }
            
            BigDecimal currentPrice = product.getPrice();
            BigDecimal lineTotal = currentPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            OrderItem orderItem = OrderItem.builder()
                    .productName(product.getName())
                    .quantity(cartItem.getQuantity())
                    .price(currentPrice)
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

    @Transactional
    public OrderResponse updateCustomerDeliveryPin(UUID id, LocationUpdateRequest request) {
        Order order = findOrderOrThrow(id);
        validateCustomerOrderOwnership(order);

        if (order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.REJECTED) {
            throw new IllegalStateException("Delivery location can no longer be changed for this order");
        }

        // Prevent a customer from checking out inside the zone and then moving the pin outside it.
        validateDeliveryRadiusCoordinates(request.getLatitude(), request.getLongitude());

        order.setCustomerLatitude(request.getLatitude());
        order.setCustomerLongitude(request.getLongitude());
        Order updated = orderRepository.save(order);
        return OrderResponse.from(updated);
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

        if (newStatus == OrderStatus.ASSIGNED && order.getDeliveryPartnerId() == null) {
            throw new IllegalStateException("Cannot transition order to ASSIGNED without an assigned delivery partner");
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.save(order);
        return OrderResponse.from(updated);
    }

    @Transactional
    public OrderResponse approveOrder(UUID id) {
        validateAdminAccess();
        Order order = findOrderOrThrow(id);

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new IllegalStateException(
                    String.format("Cannot transition order from %s to %s", order.getStatus(), OrderStatus.READY_TO_ASSIGN));
        }

        order.setStatus(OrderStatus.READY_TO_ASSIGN);
        Order updated = orderRepository.save(order);
        return OrderResponse.from(updated);
    }

    @Transactional
    public OrderResponse rejectOrder(UUID id) {
        validateAdminAccess();
        Order order = findOrderOrThrow(id);

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new IllegalStateException(
                    String.format("Cannot transition order from %s to %s", order.getStatus(), OrderStatus.REJECTED));
        }

        order.setStatus(OrderStatus.REJECTED);
        Order updated = orderRepository.save(order);
        return OrderResponse.from(updated);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID id) {
        Order order = findOrderOrThrow(id);
        validateOrderCancellationAccess(order);

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel an already cancelled order");
        }
        if (currentStatus == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel a delivered order");
        }
        if (currentStatus == OrderStatus.REJECTED) {
            throw new IllegalStateException("Cannot cancel a rejected order");
        }

        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(OrderStatus.CANCELLED)) {
            throw new IllegalStateException(
                    String.format("Cannot transition order from %s to %s", currentStatus, OrderStatus.CANCELLED));
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
        if (order.getStatus() == OrderStatus.REJECTED) {
            throw new IllegalStateException("Cannot assign delivery to a rejected order");
        }
        if (order.getStatus() == OrderStatus.PLACED) {
            throw new IllegalStateException("Cannot assign delivery to an order in PLACED state; order must be approved first");
        }
        if (order.getStatus() != OrderStatus.READY_TO_ASSIGN) {
            throw new IllegalStateException(
                    String.format("Cannot transition order from %s to ASSIGNED", order.getStatus()));
        }

        if (request == null || request.getDeliveryPartnerId() == null) {
            throw new IllegalArgumentException("Delivery partner ID is required");
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

    private void validateDeliveryRadius(CheckoutRequest request) {
        Double customerLatitude = request.getCustomerLatitude();
        Double customerLongitude = request.getCustomerLongitude();

        if (storeLatitude != null && storeLongitude != null
                && (customerLatitude == null || customerLongitude == null)) {
            throw new IllegalStateException("Please verify your current location before checkout");
        }

        validateDeliveryRadiusCoordinates(customerLatitude, customerLongitude);
    }

    private void validateDeliveryRadiusCoordinates(Double customerLatitude, Double customerLongitude) {
        // Empty store coordinates intentionally mean "radius feature not configured" so
        // existing tests/local environments keep working. Production should always set both.
        if (storeLatitude == null || storeLongitude == null) {
            return;
        }
        if (customerLatitude == null || customerLongitude == null) {
            throw new IllegalStateException("A valid customer GPS location is required");
        }

        double distanceKm = calculateDistanceKm(
                storeLatitude,
                storeLongitude,
                customerLatitude,
                customerLongitude
        );

        if (distanceKm > maxDeliveryRadiusKm) {
            throw new IllegalStateException(String.format(
                    Locale.ROOT,
                    "Delivery is available only within %.1f km. Your current location is approximately %.1f km away.",
                    maxDeliveryRadiusKm,
                    distanceKm
            ));
        }
    }

    private static double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusKm = 6371.0;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private static Double parseCoordinate(String raw, double min, double max) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value) || value < min || value > max) {
                throw new IllegalStateException("Configured store coordinate is outside the valid range");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Configured store coordinate is not a valid number", ex);
        }
    }

    private void validateCustomerOrderOwnership(Order order) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Authentication required to update this order");
        }

        boolean isCustomer = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));
        if (!isCustomer) {
            throw new AccessDeniedException("Only the customer who placed the order can update the delivery pin");
        }

        String currentUserEmail = auth.getName();
        if (order.getCustomerEmail() == null || !order.getCustomerEmail().equalsIgnoreCase(currentUserEmail)) {
            throw new AccessDeniedException("You are not authorized to update this order");
        }
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

    private void validateOrderCancellationAccess(Order order) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Authentication required to access this order");
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return; // Admin has universal order cancellation access
        }

        boolean isDeliveryPartner = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DELIVERY_PARTNER"));
        if (isDeliveryPartner) {
            throw new AccessDeniedException("Delivery partners are not authorized to cancel orders");
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
