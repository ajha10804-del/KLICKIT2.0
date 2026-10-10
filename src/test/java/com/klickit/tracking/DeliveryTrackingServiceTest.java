package com.klickit.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klickit.common.dto.ApiResponse;
import com.klickit.common.exception.GlobalExceptionHandler;
import com.klickit.common.exception.ResourceNotFoundException;
import com.klickit.delivery.entity.DeliveryPartner;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.repository.OrderRepository;
import com.klickit.tracking.controller.TrackingController;
import com.klickit.tracking.dto.LocationUpdateRequest;
import com.klickit.tracking.dto.TrackingResponse;
import com.klickit.tracking.entity.DeliveryLocation;
import com.klickit.tracking.repository.DeliveryLocationRepository;
import com.klickit.tracking.service.TrackingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeliveryTrackingServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private DeliveryLocationRepository deliveryLocationRepository;

    private TrackingService trackingService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final Instant baseTime = Instant.parse("2026-10-07T12:00:00Z");
    private Clock fixedClock;

    private UUID orderId;
    private UUID partnerId1;
    private UUID partnerId2;
    private DeliveryPartner partner1;
    private DeliveryPartner partner2;
    private Order order;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(baseTime, ZoneId.of("UTC"));
        trackingService = new TrackingService(
                orderRepository,
                deliveryPartnerRepository,
                deliveryLocationRepository,
                fixedClock
        );

        TrackingController trackingController = new TrackingController(trackingService);
        mockMvc = MockMvcBuilders.standaloneSetup(trackingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();

        orderId = UUID.randomUUID();
        partnerId1 = UUID.randomUUID();
        partnerId2 = UUID.randomUUID();

        partner1 = DeliveryPartner.builder()
                .name("Driver One")
                .phone("+919999900001")
                .build();
        partner1.setId(partnerId1);

        partner2 = DeliveryPartner.builder()
                .name("Driver Two")
                .phone("+919999900002")
                .build();
        partner2.setId(partnerId2);

        order = Order.builder()
                .customerName("Alice Customer")
                .customerEmail("alice@klickit.test")
                .customerPhone("+919876543210")
                .customerAddress("Block 6, Room 301")
                .customerLatitude(23.075327)
                .customerLongitude(76.860658)
                .customerLandmark("Near Mess 2")
                .meetAtGate(true)
                .status(OrderStatus.ASSIGNED)
                .deliveryPartnerId(partnerId1)
                .deliveryPartnerName("Driver One")
                .deliveryPartnerPhone("+919999900001")
                .totalAmount(new BigDecimal("250.00"))
                .build();
        order.setId(orderId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                )
        );
    }

    // -------------------------------------------------------------------------
    // 1. Customer read access
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("1. Customer can read tracking for their own order")
    void customer_canReadOwnOrderTracking() {
        authenticate("alice@klickit.test", "ROLE_CUSTOMER");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        DeliveryLocation location = DeliveryLocation.builder()
                .order(order)
                .deliveryPartner(partner1)
                .latitude(23.0740)
                .longitude(76.8400)
                .accuracy(15.0)
                .build();
        location.setUpdatedAt(baseTime);
        when(deliveryLocationRepository.findByOrderId(orderId)).thenReturn(Optional.of(location));

        TrackingResponse response = trackingService.getTracking(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(orderId);
        assertThat(response.getCustomerLatitude()).isEqualTo(23.075327);
        assertThat(response.getCustomerLongitude()).isEqualTo(76.860658);
        assertThat(response.getDeliveryLatitude()).isEqualTo(23.0740);
        assertThat(response.getDeliveryLongitude()).isEqualTo(76.8400);
        assertThat(response.getAccuracy()).isEqualTo(15.0);
        assertThat(response.isTrackingActive()).isTrue();
    }

    // -------------------------------------------------------------------------
    // 2. Customer unauthorized cross-order read
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("2. Customer cannot read another customer's tracking data")
    void customer_cannotReadAnotherCustomerTracking() {
        authenticate("mallory@klickit.test", "ROLE_CUSTOMER");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> trackingService.getTracking(orderId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to track this order");
    }

    // -------------------------------------------------------------------------
    // 3. Admin read access
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("3. Admin can inspect tracking for any order")
    void admin_canInspectAnyTracking() {
        authenticate("admin@klickit.test", "ROLE_ADMIN");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(deliveryLocationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        TrackingResponse response = trackingService.getTracking(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(orderId);
        assertThat(response.getDeliveryLatitude()).isNull();
    }

    // -------------------------------------------------------------------------
    // 4. Assigned delivery partner can update own order location
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("4. Assigned delivery partner can update location for their order")
    void assignedDeliveryPartner_canUpdateLocation() {
        authenticate("driver1@klickit.test", "ROLE_DELIVERY_PARTNER");
        when(deliveryPartnerRepository.findByUserEmail("driver1@klickit.test")).thenReturn(Optional.of(partner1));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(deliveryLocationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        when(deliveryLocationRepository.save(any(DeliveryLocation.class))).thenAnswer(inv -> inv.getArgument(0));

        LocationUpdateRequest request = LocationUpdateRequest.builder()
                .latitude(23.0735)
                .longitude(76.8350)
                .accuracy(8.5)
                .build();

        TrackingResponse response = trackingService.updateDeliveryLocation(orderId, request);

        assertThat(response).isNotNull();
        assertThat(response.getDeliveryLatitude()).isEqualTo(23.0735);
        assertThat(response.getDeliveryLongitude()).isEqualTo(76.8350);
        assertThat(response.getAccuracy()).isEqualTo(8.5);

        ArgumentCaptor<DeliveryLocation> captor = ArgumentCaptor.forClass(DeliveryLocation.class);
        verify(deliveryLocationRepository).save(captor.capture());
        DeliveryLocation saved = captor.getValue();
        assertThat(saved.getLatitude()).isEqualTo(23.0735);
        assertThat(saved.getLongitude()).isEqualTo(76.8350);
        assertThat(saved.getDeliveryPartner()).isEqualTo(partner1);
    }

    // -------------------------------------------------------------------------
    // 5. Delivery partner cannot update another driver's order
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("5. Delivery partner cannot update another driver's order")
    void deliveryPartner_cannotUpdateAnotherDriverOrder() {
        authenticate("driver2@klickit.test", "ROLE_DELIVERY_PARTNER");
        when(deliveryPartnerRepository.findByUserEmail("driver2@klickit.test")).thenReturn(Optional.of(partner2));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        LocationUpdateRequest request = LocationUpdateRequest.builder()
                .latitude(23.0735)
                .longitude(76.8350)
                .accuracy(10.0)
                .build();

        assertThatThrownBy(() -> trackingService.updateDeliveryLocation(orderId, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to update location for this order");

        verify(deliveryLocationRepository, never()).save(any(DeliveryLocation.class));
    }

    // -------------------------------------------------------------------------
    // 6. Unassigned order rejects driver location update
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("6. Unassigned order (no driver assigned) rejects location updates")
    void unassignedOrder_rejectsLocationUpdate() {
        order.setDeliveryPartnerId(null);
        order.setStatus(OrderStatus.PLACED);

        authenticate("driver1@klickit.test", "ROLE_DELIVERY_PARTNER");

        LocationUpdateRequest request = LocationUpdateRequest.builder()
                .latitude(23.0735)
                .longitude(76.8350)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> trackingService.updateDeliveryLocation(orderId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Live location updates are not accepted for order in status: PLACED");
    }

    // -------------------------------------------------------------------------
    // 7. Invalid coordinates rejected (out of range, NaN, Infinite)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("7. Invalid latitude (< -90 or > 90 or NaN) is rejected")
    void invalidLatitude_rejected() {
        LocationUpdateRequest invalidLat1 = LocationUpdateRequest.builder()
                .latitude(95.0).longitude(76.8).build();
        assertThatThrownBy(() -> trackingService.updateDeliveryLocation(orderId, invalidLat1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude must be a finite number between -90 and 90");

        LocationUpdateRequest nanLat = LocationUpdateRequest.builder()
                .latitude(Double.NaN).longitude(76.8).build();
        assertThatThrownBy(() -> trackingService.updateDeliveryLocation(orderId, nanLat))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude must be a finite number between -90 and 90");
    }

    @Test
    @DisplayName("8. Invalid longitude (< -180 or > 180 or Infinity) is rejected")
    void invalidLongitude_rejected() {
        LocationUpdateRequest invalidLon = LocationUpdateRequest.builder()
                .latitude(23.0).longitude(185.0).build();
        assertThatThrownBy(() -> trackingService.updateDeliveryLocation(orderId, invalidLon))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude must be a finite number between -180 and 180");

        LocationUpdateRequest infLon = LocationUpdateRequest.builder()
                .latitude(23.0).longitude(Double.POSITIVE_INFINITY).build();
        assertThatThrownBy(() -> trackingService.updateDeliveryLocation(orderId, infLon))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude must be a finite number between -180 and 180");
    }

    // -------------------------------------------------------------------------
    // 9. Negative or malformed accuracy rejected
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("9. Negative accuracy is rejected")
    void negativeAccuracy_rejected() {
        LocationUpdateRequest negativeAcc = LocationUpdateRequest.builder()
                .latitude(23.0).longitude(76.8).accuracy(-5.0).build();

        assertThatThrownBy(() -> trackingService.updateDeliveryLocation(orderId, negativeAcc))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Accuracy must be a finite, non-negative number");
    }

    // -------------------------------------------------------------------------
    // 10. Completed or cancelled order rejects live location updates
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("10. Completed order rejects location updates")
    void completedOrder_rejectsLocationUpdate() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        LocationUpdateRequest request = LocationUpdateRequest.builder()
                .latitude(23.0).longitude(76.8).build();

        assertThatThrownBy(() -> trackingService.updateDeliveryLocation(orderId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Live location updates are not accepted for order in status: DELIVERED");

        order.setStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> trackingService.updateDeliveryLocation(orderId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Live location updates are not accepted for order in status: CANCELLED");
    }

    // -------------------------------------------------------------------------
    // 11. Server-side throttling protects against high-frequency writes
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("11. Rapid location updates within 3000ms are throttled safely without duplicate DB writes")
    void rapidUpdates_throttledSafely() {
        authenticate("driver1@klickit.test", "ROLE_DELIVERY_PARTNER");
        when(deliveryPartnerRepository.findByUserEmail("driver1@klickit.test")).thenReturn(Optional.of(partner1));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        DeliveryLocation existingLocation = DeliveryLocation.builder()
                .order(order)
                .deliveryPartner(partner1)
                .latitude(23.0730)
                .longitude(76.8300)
                .accuracy(10.0)
                .build();
        // Existing location updated 1000ms ago (< 3000ms throttle interval)
        existingLocation.setUpdatedAt(baseTime.minusMillis(1000));
        when(deliveryLocationRepository.findByOrderId(orderId)).thenReturn(Optional.of(existingLocation));

        LocationUpdateRequest fastRequest = LocationUpdateRequest.builder()
                .latitude(23.0731)
                .longitude(76.8302)
                .accuracy(5.0)
                .build();

        TrackingResponse response = trackingService.updateDeliveryLocation(orderId, fastRequest);

        // Verification: Save was NOT called, throttled response returns existing coordinates
        verify(deliveryLocationRepository, never()).save(any(DeliveryLocation.class));
        assertThat(response.getDeliveryLatitude()).isEqualTo(23.0730);
        assertThat(response.getDeliveryLongitude()).isEqualTo(76.8300);
    }

    // -------------------------------------------------------------------------
    // 12. MockMvc HTTP Endpoint Integration
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("12. MockMvc GET /tracking/{orderId} returns 200 with TrackingResponse")
    void mockMvc_getTracking_returns200() throws Exception {
        authenticate("alice@klickit.test", "ROLE_CUSTOMER");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(deliveryLocationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/tracking/" + orderId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.data.customerLatitude").value(23.075327))
                .andExpect(jsonPath("$.data.meetAtGate").value(true));
    }
}
