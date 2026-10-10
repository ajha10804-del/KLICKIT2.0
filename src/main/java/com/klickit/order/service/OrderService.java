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
import com.klickit.order.entity.DrivingDistanceStatus;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderItem;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.event.OrderCreatedEvent;
import com.klickit.order.repository.OrderRepository;
import com.klickit.order.routing.DrivingDistanceResult;
import com.klickit.order.routing.DrivingDistanceService;
import com.klickit.common.dto.PagedResponse;
import com.klickit.user.entity.Role;
import com.klickit.common.util.HaversineDistanceCalculator;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
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
    private final double storeLatitude;
    private final double storeLongitude;
    private final double maxRadiusKm;
    private final double campusGateLatitude;
    private final double campusGateLongitude;
    private final double campusRadiusM;
    private final boolean enforceRange;
    private final DrivingDistanceService drivingDistanceService;

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            com.klickit.product.repository.ProductRepository productRepository,
            DeliveryPartnerRepository deliveryPartnerRepository,
            ApplicationEventPublisher eventPublisher) {
        this(orderRepository, cartRepository, productRepository, deliveryPartnerRepository, eventPublisher, Clock.systemUTC(), new BigDecimal("25.00"), new BigDecimal("199.00"), new DeliveryLocationProperties(), null);
    }

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            com.klickit.product.repository.ProductRepository productRepository,
            DeliveryPartnerRepository deliveryPartnerRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this(orderRepository, cartRepository, productRepository, deliveryPartnerRepository, eventPublisher, clock, new BigDecimal("25.00"), new BigDecimal("199.00"), new DeliveryLocationProperties(), null);
    }

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            com.klickit.product.repository.ProductRepository productRepository,
            DeliveryPartnerRepository deliveryPartnerRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            BigDecimal defaultDeliveryFee,
            BigDecimal freeDeliveryThreshold) {
        this(orderRepository, cartRepository, productRepository, deliveryPartnerRepository, eventPublisher, clock, defaultDeliveryFee, freeDeliveryThreshold, new DeliveryLocationProperties(), null);
    }

    public OrderService(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            com.klickit.product.repository.ProductRepository productRepository,
            DeliveryPartnerRepository deliveryPartnerRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            BigDecimal defaultDeliveryFee,
            BigDecimal freeDeliveryThreshold,
            DeliveryLocationProperties deliveryLocationProperties) {
        this(orderRepository, cartRepository, productRepository, deliveryPartnerRepository, eventPublisher, clock, defaultDeliveryFee, freeDeliveryThreshold, deliveryLocationProperties, null);
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
            @org.springframework.beans.factory.annotation.Autowired(required = false) DeliveryLocationProperties deliveryLocationProperties,
            @org.springframework.beans.factory.annotation.Autowired(required = false) DrivingDistanceService drivingDistanceService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.deliveryPartnerRepository = deliveryPartnerRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.defaultDeliveryFee = defaultDeliveryFee != null ? defaultDeliveryFee : new BigDecimal("25.00");
        this.freeDeliveryThreshold = freeDeliveryThreshold != null ? freeDeliveryThreshold : new BigDecimal("199.00");
        DeliveryLocationProperties props = deliveryLocationProperties != null ? deliveryLocationProperties : new DeliveryLocationProperties();
        this.storeLatitude = props.getStoreLatitude();
        this.storeLongitude = props.getStoreLongitude();
        this.maxRadiusKm = props.getMaxRadiusKm();
        this.campusGateLatitude = props.getCampusGateLatitude();
        this.campusGateLongitude = props.getCampusGateLongitude();
        this.campusRadiusM = props.getCampusRadiusM();
        this.enforceRange = props.isEnforceRange();
        this.drivingDistanceService = drivingDistanceService;
    }

    @PostConstruct
    public void validateStartupConfiguration() {
        if (!HaversineDistanceCalculator.isValidCoordinate(storeLatitude, storeLongitude)) {
            throw new IllegalStateException(String.format(
                    "Invalid store location configuration: (%.4f, %.4f) is not a valid geographic coordinate",
                    storeLatitude, storeLongitude));
        }
        if (maxRadiusKm <= 0.0 || Double.isNaN(maxRadiusKm) || Double.isInfinite(maxRadiusKm)) {
            throw new IllegalStateException(String.format(
                    "Invalid maximum store delivery radius: %.2f km must be positive and finite", maxRadiusKm));
        }
        if (!HaversineDistanceCalculator.isValidCoordinate(campusGateLatitude, campusGateLongitude)) {
            throw new IllegalStateException(String.format(
                    "Invalid campus gate location configuration: (%.4f, %.4f) is not a valid geographic coordinate",
                    campusGateLatitude, campusGateLongitude));
        }
        if (campusRadiusM <= 0.0 || Double.isNaN(campusRadiusM) || Double.isInfinite(campusRadiusM)) {
            throw new IllegalStateException(String.format(
                    "Invalid campus geofence radius: %.2f m must be positive and finite", campusRadiusM));
        }

        double storeToGateKm = HaversineDistanceCalculator.calculateDistanceKm(
                storeLatitude, storeLongitude, campusGateLatitude, campusGateLongitude);
        if (storeToGateKm > maxRadiusKm) {
            throw new IllegalStateException(String.format(
                    "Invalid delivery configuration: VIT Main Gate (%.4f, %.4f) is %.2f km away from store (%.4f, %.4f), which exceeds maximum store delivery radius of %.2f km",
                    campusGateLatitude, campusGateLongitude, storeToGateKm, storeLatitude, storeLongitude, maxRadiusKm));
        }
    }

    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        Cart cart = cartRepository.findBySessionId(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "sessionId", request.getSessionId()));

        String currentUserEmail = getCurrentUserEmail();
        String owner = cart.getCustomerEmail();
        if (owner == null || owner.isBlank()) {
            if (currentUserEmail != null && !currentUserEmail.isBlank()) {
                String normalizedEmail = currentUserEmail.trim().toLowerCase();
                int rowsUpdated = cartRepository.claimCartForCustomer(cart.getSessionId(), normalizedEmail);
                if (rowsUpdated > 0) {
                    cart.setCustomerEmail(normalizedEmail);
                } else {
                    Cart reloaded = cartRepository.findBySessionId(cart.getSessionId()).orElse(cart);
                    String updatedOwner = reloaded.getCustomerEmail();
                    if (updatedOwner != null && !updatedOwner.equalsIgnoreCase(normalizedEmail)) {
                        throw new org.springframework.security.access.AccessDeniedException("Access denied to cart");
                    }
                    cart.setCustomerEmail(normalizedEmail);
                }
            }
        } else if (currentUserEmail == null || !owner.equalsIgnoreCase(currentUserEmail.trim())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied to cart");
        }

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty cart");
        }

        boolean meetAtGate = false;
        Double lat = request.getCustomerLatitude();
        Double lon = request.getCustomerLongitude();

        if (enforceRange) {
            if (lat == null || lon == null) {
                throw new IllegalArgumentException("Valid delivery latitude and longitude are required");
            }
            if (Double.isNaN(lat) || Double.isInfinite(lat) || Double.isNaN(lon) || Double.isInfinite(lon)) {
                throw new IllegalArgumentException("Valid delivery latitude and longitude are required");
            }
            if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
                throw new IllegalArgumentException("Latitude must be between -90 and 90, and longitude between -180 and 180");
            }

            double distanceToStoreKm = HaversineDistanceCalculator.calculateDistanceKm(
                    storeLatitude, storeLongitude, lat, lon);
            if (distanceToStoreKm > maxRadiusKm + 1e-6) {
                throw new IllegalArgumentException(
                        String.format("Delivery location is outside our service area (maximum radius: %.1f km)", maxRadiusKm));
            }

            double distanceToGateMeters = HaversineDistanceCalculator.calculateDistanceMeters(
                    campusGateLatitude, campusGateLongitude, lat, lon);
            meetAtGate = distanceToGateMeters <= campusRadiusM;
        } else if (lat != null && lon != null && HaversineDistanceCalculator.isValidCoordinate(lat, lon)) {
            double distanceToGateMeters = HaversineDistanceCalculator.calculateDistanceMeters(
                    campusGateLatitude, campusGateLongitude, lat, lon);
            meetAtGate = distanceToGateMeters <= campusRadiusM;
        }

        // Authoritative 15-minute fulfillment SLA calculated server-side; client value is untrusted
        Instant authoritativeDeadline = Instant.now(clock).plus(15, ChronoUnit.MINUTES);

        String trimmedAddress = request.getCustomerAddress() != null && !request.getCustomerAddress().isBlank()
                ? request.getCustomerAddress().trim()
                : null;
        String trimmedLandmark = request.getCustomerLandmark() != null && !request.getCustomerLandmark().isBlank()
                ? request.getCustomerLandmark().trim()
                : null;

        Double destLat = null;
        Double destLng = null;
        if (meetAtGate) {
            destLat = campusGateLatitude;
            destLng = campusGateLongitude;
        } else if (lat != null && lon != null) {
            destLat = lat;
            destLng = lon;
        }

        Integer distanceMeters = null;
        DrivingDistanceStatus distanceStatus = DrivingDistanceStatus.UNAVAILABLE;
        Double evaluatedLat = null;
        Double evaluatedLng = null;

        if (destLat != null && destLng != null && HaversineDistanceCalculator.isValidCoordinate(destLat, destLng)) {
            evaluatedLat = destLat;
            evaluatedLng = destLng;
            if (drivingDistanceService != null) {
                try {
                    DrivingDistanceResult distanceResult = drivingDistanceService.calculateDistance(
                            storeLatitude, storeLongitude, destLat, destLng);
                    if (distanceResult != null) {
                        distanceMeters = distanceResult.getDistanceMeters();
                        distanceStatus = distanceResult.getStatus() != null
                                ? distanceResult.getStatus()
                                : DrivingDistanceStatus.UNAVAILABLE;
                    }
                } catch (Exception ex) {
                    log.warn("Driving distance calculation failed during checkout: {}", ex.getMessage());
                    distanceStatus = DrivingDistanceStatus.UNAVAILABLE;
                }
            }
        }

        Order order = Order.builder()
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .customerAddress(trimmedAddress)
                .customerLatitude(lat)
                .customerLongitude(lon)
                .customerLandmark(trimmedLandmark)
                .meetAtGate(meetAtGate)
                .customerEmail(getCurrentUserEmail())
                .deadline(authoritativeDeadline)
                .totalAmount(BigDecimal.ZERO)
                .drivingDistanceMeters(distanceMeters)
                .drivingDistanceStatus(distanceStatus)
                .drivingDistanceDestinationLat(evaluatedLat)
                .drivingDistanceDestinationLng(evaluatedLng)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;

        // Process cart items in deterministic ascending product ID order to prevent deadlock
        List<CartItem> sortedCartItems = cart.getItems().stream()
                .sorted(Comparator.comparing(CartItem::getProductId))
                .toList();

        for (CartItem cartItem : sortedCartItems) {
            com.klickit.product.entity.Product product = productRepository.findById(cartItem.getProductId())
                    .orElseThrow(() -> new IllegalStateException("Product not found: " + cartItem.getProductName()));
            
            if (!product.isActive()) {
                throw new IllegalStateException("Product is no longer available: " + product.getName());
            }

            // Atomic conditional stock decrement requiring active = true and stock >= quantity
            int rowsUpdated = productRepository.decrementStockIfAvailable(product.getId(), cartItem.getQuantity());
            if (rowsUpdated == 0) {
                throw new IllegalStateException("Insufficient stock for product: " + product.getName());
            }
            
            BigDecimal currentPrice = product.getPrice();
            BigDecimal lineTotal = currentPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            OrderItem orderItem = OrderItem.builder()
                    .productName(product.getName())
                    .productId(product.getId())
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

    public List<OrderResponse> getCustomerOrders() {
        String email = getCurrentUserEmail();
        if (email == null) {
            throw new AccessDeniedException("Authentication required to view your orders");
        }
        return orderRepository.findByCustomerEmailOrderByCreatedAtDesc(email).stream()
                .map(OrderResponse::from)
                .toList();
    }

    public PagedResponse<OrderResponse> getAdminOrdersPaged(OrderStatus status, Integer page, Integer size) {
        validateAdminAccess();

        int pageNum = page != null ? Math.max(0, page) : 0;
        int pageSize = size != null ? Math.min(100, Math.max(1, size)) : 20;

        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<Order> orderPage = (status != null)
                ? orderRepository.findByStatus(status, pageable)
                : orderRepository.findAllBy(pageable);

        Page<OrderResponse> responsePage = orderPage.map(OrderResponse::from);
        return PagedResponse.from(responsePage);
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

        if (newStatus == OrderStatus.READY_TO_ASSIGN) {
            verifyDrivingDistanceForApproval(order);
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

        verifyDrivingDistanceForApproval(order);

        order.setStatus(OrderStatus.READY_TO_ASSIGN);
        Order updated = orderRepository.save(order);
        return OrderResponse.from(updated);
    }

    private void verifyDrivingDistanceForApproval(Order order) {
        Double destLat = null;
        Double destLng = null;
        if (order.isMeetAtGate()) {
            destLat = campusGateLatitude;
            destLng = campusGateLongitude;
        } else if (order.getCustomerLatitude() != null && order.getCustomerLongitude() != null) {
            destLat = order.getCustomerLatitude();
            destLng = order.getCustomerLongitude();
        }

        if (destLat == null || destLng == null || !HaversineDistanceCalculator.isValidCoordinate(destLat, destLng)) {
            throw new IllegalStateException("Cannot approve order: delivery destination coordinates are missing or invalid");
        }

        boolean destinationMatches = order.getDrivingDistanceDestinationLat() != null
                && order.getDrivingDistanceDestinationLng() != null
                && Math.abs(order.getDrivingDistanceDestinationLat() - destLat) < 1e-5
                && Math.abs(order.getDrivingDistanceDestinationLng() - destLng) < 1e-5;

        // If route was not verified or calculated for a different destination, attempt bounded on-demand recalculation
        if (!destinationMatches
                || order.getDrivingDistanceStatus() == DrivingDistanceStatus.UNAVAILABLE
                || order.getDrivingDistanceStatus() == DrivingDistanceStatus.PENDING
                || order.getDrivingDistanceMeters() == null) {
            if (drivingDistanceService != null) {
                try {
                    DrivingDistanceResult result = drivingDistanceService.calculateDistance(
                            storeLatitude, storeLongitude, destLat, destLng);
                    if (result != null) {
                        order.setDrivingDistanceMeters(result.getDistanceMeters());
                        order.setDrivingDistanceStatus(result.getStatus() != null ? result.getStatus() : DrivingDistanceStatus.UNAVAILABLE);
                        order.setDrivingDistanceDestinationLat(destLat);
                        order.setDrivingDistanceDestinationLng(destLng);
                        destinationMatches = true;
                    }
                } catch (Exception ex) {
                    log.warn("On-demand driving distance recalculation failed for order ID {}: {}", order.getId(), ex.getMessage());
                    order.setDrivingDistanceStatus(DrivingDistanceStatus.UNAVAILABLE);
                }
            }
        }

        if (!destinationMatches) {
            throw new IllegalStateException("Cannot approve order: route distance was not calculated for order delivery destination");
        }

        if (order.getDrivingDistanceStatus() == DrivingDistanceStatus.EXCEEDED
                || (order.getDrivingDistanceMeters() != null && order.getDrivingDistanceMeters() > 5000)) {
            throw new IllegalStateException(String.format(
                    "Cannot approve order: verified road driving distance (%d m) exceeds the 5,000 metre limit",
                    order.getDrivingDistanceMeters()));
        }

        if (order.getDrivingDistanceStatus() != DrivingDistanceStatus.ELIGIBLE
                || order.getDrivingDistanceMeters() == null) {
            throw new IllegalStateException("Cannot approve order: road driving distance could not be verified. Please retry.");
        }
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
        restoreOrderInventory(updated);
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
        restoreOrderInventory(updated);
        return OrderResponse.from(updated);
    }

    private void restoreOrderInventory(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }

        for (OrderItem item : order.getItems()) {
            if (item.getProductId() == null) {
                log.warn("Skipping inventory restoration for historical order item ID {} with null productId (product name: {})",
                        item.getId(), item.getProductName());
                continue;
            }

            int rowsUpdated = productRepository.incrementStock(item.getProductId(), item.getQuantity());
            if (rowsUpdated == 0) {
                log.warn("Product ID {} no longer exists during inventory restoration for order ID {}",
                        item.getProductId(), order.getId());
            }
        }
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
