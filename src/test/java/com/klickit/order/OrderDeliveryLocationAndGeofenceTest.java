package com.klickit.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.cart.entity.Cart;
import com.klickit.cart.entity.CartItem;
import com.klickit.cart.repository.CartRepository;
import com.klickit.common.dto.ApiResponse;
import com.klickit.common.exception.GlobalExceptionHandler;
import com.klickit.common.util.HaversineDistanceCalculator;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.order.controller.OrderController;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.repository.OrderRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderDeliveryLocationAndGeofenceTest {

    private static final double STORE_LAT = 23.073427650432762;
    private static final double STORE_LON = 76.82864818972119;
    private static final double MAX_RADIUS_KM = 6.0;
    private static final double GATE_LAT = 23.075611;
    private static final double GATE_LON = 76.850082;
    private static final double CAMPUS_RADIUS_M = 1100.0;
    private static final double BLOCK6_LAT = 23.075327;
    private static final double BLOCK6_LON = 76.860658;

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

    private OrderService orderService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final String sessionId = "sess_geofence_123";
    private Cart activeCart;
    private Product activeProduct;
    private UUID productId;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                cartRepository,
                productRepository,
                deliveryPartnerRepository,
                eventPublisher,
                Clock.systemUTC(),
                new BigDecimal("25.00"),
                new BigDecimal("199.00"),
                new DeliveryLocationProperties(
                        STORE_LAT,
                        STORE_LON,
                        MAX_RADIUS_KM,
                        GATE_LAT,
                        GATE_LON,
                        CAMPUS_RADIUS_M,
                        true // enforceRange = true
                )
        );

        OrderController orderController = new OrderController(orderService);
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "customer@klickit.test",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                )
        );

        productId = UUID.randomUUID();
        activeProduct = Product.builder()
                .name("Campus Essentials")
                .price(new BigDecimal("100.00"))
                .active(true)
                .build();
        activeProduct.setId(productId);

        activeCart = Cart.builder()
                .sessionId(sessionId)
                .items(new ArrayList<>())
                .build();
        activeCart.addItem(CartItem.builder()
                .cart(activeCart)
                .productId(productId)
                .productName("Campus Essentials")
                .unitPrice(new BigDecimal("100.00"))
                .quantity(1)
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void stubCartAndProduct() {
        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(activeCart));
        when(productRepository.findById(productId)).thenReturn(Optional.of(activeProduct));
        org.mockito.Mockito.lenient().when(productRepository.decrementStockIfAvailable(org.mockito.ArgumentMatchers.eq(productId), org.mockito.ArgumentMatchers.any(int.class))).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });
    }

    // -------------------------------------------------------------------------
    // 1. Store Radius: Inside (Valid point ~2.5 km from store -> Order succeeds)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 1: Valid point ~2.2 km inside store radius succeeds (HTTP 201) with coordinates saved")
    void case1_validPointInsideRadius_succeeds() {
        stubCartAndProduct();

        double customerLat = 23.0740;
        double customerLon = 76.8350;
        double distanceKm = HaversineDistanceCalculator.calculateDistanceKm(STORE_LAT, STORE_LON, customerLat, customerLon);
        assertThat(distanceKm).isLessThanOrEqualTo(MAX_RADIUS_KM);

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "Block A, Campus Road",
                customerLat, customerLon, "Near Gate 1"
        );

        OrderResponse response = orderService.checkout(request);

        assertThat(response).isNotNull();
        assertThat(response.getCustomerLatitude()).isEqualTo(customerLat);
        assertThat(response.getCustomerLongitude()).isEqualTo(customerLon);
        assertThat(response.getCustomerLandmark()).isEqualTo("Near Gate 1");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getCustomerLatitude()).isEqualTo(customerLat);
        assertThat(savedOrder.getCustomerLongitude()).isEqualTo(customerLon);
    }

    // -------------------------------------------------------------------------
    // 2. Store Radius: Outside (Point just beyond 6.0 km -> 400 with exact message)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 2: Point just beyond 6.0 km outside store radius throws 400 with exact service area message")
    void case2_pointOutsideRadius_throws400() {
        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(activeCart));

        double farLat = STORE_LAT + Math.toDegrees(6.05 / 6371.0);
        double farLon = STORE_LON;
        double distanceKm = HaversineDistanceCalculator.calculateDistanceKm(STORE_LAT, STORE_LON, farLat, farLon);
        assertThat(distanceKm).isGreaterThan(MAX_RADIUS_KM);

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "Distant Highway Road",
                farLat, farLon, "Highway milestone"
        );

        assertThatThrownBy(() -> orderService.checkout(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Delivery location is outside our service area (maximum radius: 6.0 km)");
    }

    // -------------------------------------------------------------------------
    // 3. Store Radius: Boundary (Point at exactly 6.00 km -> Accepted)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 3: Point at exactly 6.00 km boundary is accepted")
    void case3_pointAtExactBoundary_accepted() {
        stubCartAndProduct();

        double boundaryLat = STORE_LAT + Math.toDegrees(6.0 / 6371.0);
        double boundaryLon = STORE_LON;
        double distanceKm = HaversineDistanceCalculator.calculateDistanceKm(STORE_LAT, STORE_LON, boundaryLat, boundaryLon);
        assertThat(distanceKm).isCloseTo(6.0, org.assertj.core.data.Offset.offset(0.001));

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "Perimeter boundary point",
                boundaryLat, boundaryLon, "Boundary marker"
        );

        OrderResponse response = orderService.checkout(request);
        assertThat(response).isNotNull();
        assertThat(response.getCustomerLatitude()).isEqualTo(boundaryLat);
    }

    // -------------------------------------------------------------------------
    // 4 & 5. Coordinate Validation: Missing Latitude / Longitude -> 400
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 4 & 5: Missing latitude or missing longitude throws 400")
    void case4and5_missingCoordinates_throws400() {
        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(activeCart));

        CheckoutRequest missingLat = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "123 Street Address",
                null, 75.8577, "Landmark"
        );
        assertThatThrownBy(() -> orderService.checkout(missingLat))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valid delivery latitude and longitude are required");

        CheckoutRequest missingLon = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "123 Street Address",
                22.7196, null, "Landmark"
        );
        assertThatThrownBy(() -> orderService.checkout(missingLon))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valid delivery latitude and longitude are required");
    }

    // -------------------------------------------------------------------------
    // 6 & 7. Coordinate Validation: Latitude / Longitude Out of Bounds -> 400
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 6 & 7: Latitude > 90 or Longitude > 180 throws 400")
    void case6and7_outOfBoundsCoordinates_throws400() {
        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(activeCart));

        CheckoutRequest latOutOfRange = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "123 Street Address",
                95.0, 75.8577, "Landmark"
        );
        assertThatThrownBy(() -> orderService.checkout(latOutOfRange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Latitude must be between -90 and 90, and longitude between -180 and 180");

        CheckoutRequest lonOutOfRange = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "123 Street Address",
                22.7196, 185.0, "Landmark"
        );
        assertThatThrownBy(() -> orderService.checkout(lonOutOfRange))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Latitude must be between -90 and 90, and longitude between -180 and 180");
    }

    // -------------------------------------------------------------------------
    // 8. Coordinate Validation: NaN / Infinite -> 400
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 8: NaN or Infinite coordinate values throw 400")
    void case8_nanOrInfiniteCoordinates_throws400() {
        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(activeCart));

        CheckoutRequest nanRequest = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "123 Street Address",
                Double.NaN, 75.8577, "Landmark"
        );
        assertThatThrownBy(() -> orderService.checkout(nanRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valid delivery latitude and longitude are required");

        CheckoutRequest infRequest = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "123 Street Address",
                22.7196, Double.POSITIVE_INFINITY, "Landmark"
        );
        assertThatThrownBy(() -> orderService.checkout(infRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valid delivery latitude and longitude are required");
    }

    // -------------------------------------------------------------------------
    // 9 & 10. Address Validation: Address > 255 chars rejected (HTTP 400); Blank address allowed (HTTP 201)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 9 & 10: Address > 255 chars rejected (HTTP 400); blank address accepted in map-first flow (HTTP 201)")
    void case9and10_addressValidation_failsWith400() throws Exception {
        CheckoutRequest longAddress = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "A".repeat(256),
                STORE_LAT, STORE_LON, "Landmark"
        );
        mockMvc.perform(post("/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longAddress)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        stubCartAndProduct();
        CheckoutRequest blankAddress = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "",
                STORE_LAT, STORE_LON, "Landmark"
        );
        mockMvc.perform(post("/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankAddress)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.customerAddress").value(org.hamcrest.Matchers.nullValue()));
    }

    // -------------------------------------------------------------------------
    // 11. Campus Geofence: Inside campus radius (Main Gate and Block 6) -> meetAtGate = true
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 11: Point inside VIT campus radius (Main Gate and Block 6) assigns meetAtGate = true")
    void case11_pointInsideCampus_meetAtGateIsTrue() {
        stubCartAndProduct();

        // 1. Point at Block 6 hostel (~1082.37 m from Main Gate <= 1100.0 m)
        double distB6Meters = HaversineDistanceCalculator.calculateDistanceMeters(GATE_LAT, GATE_LON, BLOCK6_LAT, BLOCK6_LON);
        assertThat(distB6Meters).isCloseTo(1082.37, org.assertj.core.data.Offset.offset(1.0));
        assertThat(distB6Meters).isLessThanOrEqualTo(CAMPUS_RADIUS_M);

        CheckoutRequest requestB6 = new CheckoutRequest(
                sessionId, "Student B6", "9876543210", "Hostel Block 6",
                BLOCK6_LAT, BLOCK6_LON, "Room 304"
        );

        OrderResponse responseB6 = orderService.checkout(requestB6);
        assertThat(responseB6.isMeetAtGate()).isTrue();

        // Replenish cart for second checkout attempt
        activeCart.addItem(CartItem.builder()
                .cart(activeCart)
                .productId(productId)
                .productName("Campus Essentials")
                .unitPrice(new BigDecimal("100.00"))
                .quantity(1)
                .build());

        // 2. Point around Main Gate itself (0 m <= 1100.0 m)
        double distGateMeters = HaversineDistanceCalculator.calculateDistanceMeters(GATE_LAT, GATE_LON, GATE_LAT, GATE_LON);
        assertThat(distGateMeters).isEqualTo(0.0);

        CheckoutRequest requestGate = new CheckoutRequest(
                sessionId, "Visitor", "9876543210", "Main Security Gate",
                GATE_LAT, GATE_LON, "Gate Booth"
        );

        OrderResponse responseGate = orderService.checkout(requestGate);
        assertThat(responseGate.isMeetAtGate()).isTrue();
    }

    // -------------------------------------------------------------------------
    // 12. Campus Geofence: Outside campus radius -> meetAtGate = false
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 12: Point within 6 km store radius but outside campus radius assigns meetAtGate = false")
    void case12_pointOutsideCampus_meetAtGateIsFalse() {
        stubCartAndProduct();

        // Point ~2.05 km west of store: inside 6 km store radius, but ~4.25 km from Main Gate (outside campus)
        double offCampusLat = STORE_LAT;
        double offCampusLon = STORE_LON - 0.02;
        double distMeters = HaversineDistanceCalculator.calculateDistanceMeters(GATE_LAT, GATE_LON, offCampusLat, offCampusLon);
        assertThat(distMeters).isGreaterThan(CAMPUS_RADIUS_M);
        double distStoreKm = HaversineDistanceCalculator.calculateDistanceKm(STORE_LAT, STORE_LON, offCampusLat, offCampusLon);
        assertThat(distStoreKm).isLessThanOrEqualTo(MAX_RADIUS_KM);

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "City Resident", "9876543210", "Apartment 12, Main Road",
                offCampusLat, offCampusLon, "Opposite Park"
        );

        OrderResponse response = orderService.checkout(request);
        assertThat(response.isMeetAtGate()).isFalse();

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().isMeetAtGate()).isFalse();
    }

    // -------------------------------------------------------------------------
    // 13 & 14. Tamper Resistance: Backend authoritatively derives meetAtGate
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 13 & 14: Client cannot spoof meetAtGate flag (backend overrides client payload)")
    void case13and14_tamperResistance_backendOverridesClient() {
        stubCartAndProduct();

        // 13. Client sends meetAtGate=false for coordinates inside campus (Block 6) -> backend still stores true
        CheckoutRequest fakeFalse = new CheckoutRequest(
                sessionId, "Student", "9876543210", "Hostel Block 6",
                BLOCK6_LAT, BLOCK6_LON, "Room 304"
        );
        fakeFalse.setMeetAtGate(false); // Tamper attempt

        OrderResponse res1 = orderService.checkout(fakeFalse);
        assertThat(res1.isMeetAtGate()).isTrue();

        // Replenish cart for second checkout attempt
        activeCart.addItem(CartItem.builder()
                .cart(activeCart)
                .productId(productId)
                .productName("Campus Essentials")
                .unitPrice(new BigDecimal("100.00"))
                .quantity(1)
                .build());

        // 14. Client sends meetAtGate=true for coordinates outside campus -> backend still stores false
        double offCampusLat = STORE_LAT;
        double offCampusLon = STORE_LON - 0.02;
        CheckoutRequest fakeTrue = new CheckoutRequest(
                sessionId, "Resident", "9876543210", "City Apt",
                offCampusLat, offCampusLon, "Main Road"
        );
        fakeTrue.setMeetAtGate(true); // Tamper attempt

        OrderResponse res2 = orderService.checkout(fakeTrue);
        assertThat(res2.isMeetAtGate()).isFalse();
    }

    // -------------------------------------------------------------------------
    // 15 & 16. Atomicity: Rejected checkout saves 0 orders and leaves cart intact
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 15 & 16: Out-of-range checkout saves 0 orders and preserves cart items intact")
    void case15and16_outOfRangeCheckout_cartRemainsIntactAndNoOrderCreated() {
        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(activeCart));

        double farLat = STORE_LAT + 0.1; // Far outside 6.0 km
        double farLon = STORE_LON;

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "Distant Highway",
                farLat, farLon, "Milestone"
        );

        int originalCartSize = activeCart.getItems().size();
        assertThat(originalCartSize).isGreaterThan(0);

        assertThatThrownBy(() -> orderService.checkout(request))
                .isInstanceOf(IllegalArgumentException.class);

        // Verify NO order was saved
        verify(orderRepository, never()).save(any(Order.class));
        // Verify cart items were NOT cleared
        assertThat(activeCart.getItems()).hasSize(originalCartSize);
        verify(cartRepository, never()).save(activeCart);
    }

    // -------------------------------------------------------------------------
    // 17. Legacy Compatibility: Historical orders without coordinates load cleanly
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 17: Historical order with null coordinates loads cleanly into OrderResponse without NPE")
    void case17_legacyOrderWithoutCoordinates_loadsCleanly() {
        Order historicalOrder = Order.builder()
                .customerName("Historical Customer")
                .customerPhone("9876543210")
                .customerAddress("Historical Address")
                .customerLatitude(null)
                .customerLongitude(null)
                .customerLandmark(null)
                .meetAtGate(false)
                .totalAmount(new BigDecimal("150.00"))
                .status(OrderStatus.DELIVERED)
                .items(List.of())
                .build();
        historicalOrder.setId(UUID.randomUUID());

        OrderResponse response = OrderResponse.from(historicalOrder);

        assertThat(response).isNotNull();
        assertThat(response.getCustomerLatitude()).isNull();
        assertThat(response.getCustomerLongitude()).isNull();
        assertThat(response.getCustomerLandmark()).isNull();
        assertThat(response.isMeetAtGate()).isFalse();
    }

    // -------------------------------------------------------------------------
    // 18. Configuration Invariant: Main Gate outside store radius fails startup
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 18: Main Gate configured outside store delivery radius throws IllegalStateException on startup")
    void case18_gateOutsideStoreRadius_failsStartupInvariant() {
        double invalidGateLat = STORE_LAT + 0.2; // Far away from store (~22 km)
        double invalidGateLon = STORE_LON;

        OrderService brokenConfigService = new OrderService(
                orderRepository,
                cartRepository,
                productRepository,
                deliveryPartnerRepository,
                eventPublisher,
                Clock.systemUTC(),
                new BigDecimal("25.00"),
                new BigDecimal("199.00"),
                new DeliveryLocationProperties(
                        STORE_LAT,
                        STORE_LON,
                        MAX_RADIUS_KM,
                        invalidGateLat,
                        invalidGateLon,
                        CAMPUS_RADIUS_M,
                        true
                )
        );

        assertThatThrownBy(brokenConfigService::validateStartupConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds maximum store delivery radius of 6.00 km");
    }

    // -------------------------------------------------------------------------
    // 19. Real Configuration Verification: Main Gate, Block 6 & Startup Invariant
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 19: Real configuration invariant passes and calculated distances match expected geodesic values")
    void case19_realConfiguration_distancesAndStartupInvariantValid() {
        // 1. Startup invariant passes for real configured store and gate coordinates
        orderService.validateStartupConfiguration();

        // 2. Real Store -> Main Gate distance is ~2.21 km (inside 6.0 km radius)
        double storeToGateKm = HaversineDistanceCalculator.calculateDistanceKm(STORE_LAT, STORE_LON, GATE_LAT, GATE_LON);
        assertThat(storeToGateKm).isCloseTo(2.206, org.assertj.core.data.Offset.offset(0.01));
        assertThat(storeToGateKm).isLessThan(MAX_RADIUS_KM);

        // 3. Real Main Gate -> Block 6 distance is ~1082.37 m (inside 1100.0 m campus radius)
        double gateToBlock6M = HaversineDistanceCalculator.calculateDistanceMeters(GATE_LAT, GATE_LON, BLOCK6_LAT, BLOCK6_LON);
        assertThat(gateToBlock6M).isCloseTo(1082.37, org.assertj.core.data.Offset.offset(1.0));
        assertThat(gateToBlock6M).isLessThanOrEqualTo(CAMPUS_RADIUS_M);
    }

    // -------------------------------------------------------------------------
    // 20. Fail-Safe Configuration Checks: Invalid Store/Radius Configuration Fails Startup
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 20: Invalid store coordinates or non-positive radius fails startup cleanly")
    void case20_invalidStoreOrRadiusConfig_failsStartup() {
        // Invalid store latitude (> 90)
        OrderService invalidStoreLat = new OrderService(
                orderRepository, cartRepository, productRepository, deliveryPartnerRepository, eventPublisher,
                Clock.systemUTC(), new BigDecimal("25.00"), new BigDecimal("199.00"),
                new DeliveryLocationProperties(95.0, STORE_LON, MAX_RADIUS_KM, GATE_LAT, GATE_LON, CAMPUS_RADIUS_M, true)
        );
        assertThatThrownBy(invalidStoreLat::validateStartupConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid store location configuration");

        // Non-positive store radius (0.0 or negative)
        OrderService zeroRadius = new OrderService(
                orderRepository, cartRepository, productRepository, deliveryPartnerRepository, eventPublisher,
                Clock.systemUTC(), new BigDecimal("25.00"), new BigDecimal("199.00"),
                new DeliveryLocationProperties(STORE_LAT, STORE_LON, 0.0, GATE_LAT, GATE_LON, CAMPUS_RADIUS_M, true)
        );
        assertThatThrownBy(zeroRadius::validateStartupConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid maximum store delivery radius");
    }

    // -------------------------------------------------------------------------
    // 21. Frontend Fallback String Security: Cannot bypass missing coordinates
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 21: Fallback string 'Location pinned on map' with null coordinates is rejected when enforcement is enabled")
    void case21_fallbackStringWithoutCoordinates_rejected() {
        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(activeCart));

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "Location pinned on map",
                null, null, "Near Gate"
        );

        assertThatThrownBy(() -> orderService.checkout(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valid delivery latitude and longitude are required");
    }

    // -------------------------------------------------------------------------
    // 22. Test Profile Invariant: enforcement can still be disabled intentionally
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 22: When enforceRange is false, null coordinates do not fail checkout")
    void case22_disabledEnforcement_allowsNullCoordinates() {
        stubCartAndProduct();

        OrderService disabledEnforcementService = new OrderService(
                orderRepository, cartRepository, productRepository, deliveryPartnerRepository, eventPublisher,
                Clock.systemUTC(), new BigDecimal("25.00"), new BigDecimal("199.00"),
                new DeliveryLocationProperties(STORE_LAT, STORE_LON, MAX_RADIUS_KM, GATE_LAT, GATE_LON, CAMPUS_RADIUS_M, false)
        );

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "123 Campus Lane",
                null, null, "Landmark"
        );

        OrderResponse response = disabledEnforcementService.checkout(request);
        assertThat(response).isNotNull();
        assertThat(response.isMeetAtGate()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Task 4: Clean Customer Delivery-Location Representation
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 23: Map-only checkout (null customerAddress, null landmark) succeeds and persists coordinates as authoritative truth")
    void case23_mapOnlyCheckout_nullAddress_persistsCleanly() {
        stubCartAndProduct();

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", null,
                GATE_LAT, GATE_LON, null
        );

        OrderResponse response = orderService.checkout(request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order savedOrder = captor.getValue();

        assertThat(savedOrder.getCustomerAddress()).isNull();
        assertThat(savedOrder.getCustomerLandmark()).isNull();
        assertThat(savedOrder.getCustomerLatitude()).isEqualTo(GATE_LAT);
        assertThat(savedOrder.getCustomerLongitude()).isEqualTo(GATE_LON);
        assertThat(savedOrder.isMeetAtGate()).isTrue();
        assertThat(response.getCustomerAddress()).isNull();
        assertThat(response.getCustomerLatitude()).isEqualTo(GATE_LAT);
        assertThat(response.getCustomerLongitude()).isEqualTo(GATE_LON);
    }

    @Test
    @DisplayName("Case 24: Map + landmark checkout (null customerAddress, present landmark) succeeds and persists human instructions without address")
    void case24_mapAndLandmarkCheckout_nullAddress_persistsCleanly() {
        stubCartAndProduct();

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "   ",
                GATE_LAT, GATE_LON, "Hostel 5, Room 214"
        );

        OrderResponse response = orderService.checkout(request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order savedOrder = captor.getValue();

        assertThat(savedOrder.getCustomerAddress()).isNull();
        assertThat(savedOrder.getCustomerLandmark()).isEqualTo("Hostel 5, Room 214");
        assertThat(savedOrder.getCustomerLatitude()).isEqualTo(GATE_LAT);
        assertThat(savedOrder.getCustomerLongitude()).isEqualTo(GATE_LON);
        assertThat(response.getCustomerAddress()).isNull();
        assertThat(response.getCustomerLandmark()).isEqualTo("Hostel 5, Room 214");
    }

    @Test
    @DisplayName("Case 25: Traditional address checkout with valid coordinates preserves both address and coordinates")
    void case25_traditionalAddressCheckout_withCoordinates_persistsAddress() {
        stubCartAndProduct();

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "123 Campus Lane",
                GATE_LAT, GATE_LON, "Near Library"
        );

        OrderResponse response = orderService.checkout(request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order savedOrder = captor.getValue();

        assertThat(savedOrder.getCustomerAddress()).isEqualTo("123 Campus Lane");
        assertThat(savedOrder.getCustomerLandmark()).isEqualTo("Near Library");
        assertThat(savedOrder.getCustomerLatitude()).isEqualTo(GATE_LAT);
        assertThat(savedOrder.getCustomerLongitude()).isEqualTo(GATE_LON);
        assertThat(response.getCustomerAddress()).isEqualTo("123 Campus Lane");
        assertThat(response.getCustomerLandmark()).isEqualTo("Near Library");
    }

    @Test
    @DisplayName("Case 26: Address-only checkout without coordinates is rejected when geofence enforcement is active")
    void case26_addressOnlyWithoutCoordinates_whenEnforcementEnabled_rejected() {
        when(cartRepository.findBySessionId(sessionId)).thenReturn(Optional.of(activeCart));

        CheckoutRequest request = new CheckoutRequest(
                sessionId, "John Doe", "9876543210", "123 Campus Lane",
                null, null, "Near Library"
        );

        assertThatThrownBy(() -> orderService.checkout(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valid delivery latitude and longitude are required");

        verify(orderRepository, never()).save(any(Order.class));
    }
}
