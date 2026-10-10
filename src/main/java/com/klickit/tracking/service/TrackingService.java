package com.klickit.tracking.service;

import com.klickit.common.exception.ResourceNotFoundException;
import com.klickit.delivery.entity.DeliveryPartner;
import com.klickit.delivery.repository.DeliveryPartnerRepository;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.repository.OrderRepository;
import com.klickit.tracking.dto.LocationUpdateRequest;
import com.klickit.tracking.dto.TrackingResponse;
import com.klickit.tracking.entity.DeliveryLocation;
import com.klickit.tracking.repository.DeliveryLocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingService {

    private static final long MIN_UPDATE_INTERVAL_MS = 3000; // 3-second server throttle

    private final OrderRepository orderRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DeliveryLocationRepository deliveryLocationRepository;
    private final Clock clock;

    @Autowired
    public TrackingService(
            OrderRepository orderRepository,
            DeliveryPartnerRepository deliveryPartnerRepository,
            DeliveryLocationRepository deliveryLocationRepository) {
        this(orderRepository, deliveryPartnerRepository, deliveryLocationRepository, Clock.systemUTC());
    }

    @Transactional(readOnly = true)
    public TrackingResponse getTracking(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        validateTrackingReadAccess(order);

        Optional<DeliveryLocation> locationOpt = deliveryLocationRepository.findByOrderId(orderId);
        return buildTrackingResponse(order, locationOpt.orElse(null));
    }

    @Transactional
    public TrackingResponse updateDeliveryLocation(UUID orderId, LocationUpdateRequest request) {
        validateCoordinates(request);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        validateOrderStatusForTracking(order);

        DeliveryPartner currentPartner = getAuthenticatedDeliveryPartner();
        if (order.getDeliveryPartnerId() == null || !order.getDeliveryPartnerId().equals(currentPartner.getId())) {
            throw new AccessDeniedException("You are not authorized to update location for this order");
        }

        Optional<DeliveryLocation> existingOpt = deliveryLocationRepository.findByOrderId(orderId);
        Instant now = Instant.now(clock);

        if (existingOpt.isPresent()) {
            DeliveryLocation existing = existingOpt.get();
            // Throttling: If updated recently within 3s window, ignore DB write and return current response
            if (existing.getUpdatedAt() != null &&
                    (now.toEpochMilli() - existing.getUpdatedAt().toEpochMilli() < MIN_UPDATE_INTERVAL_MS)) {
                log.debug("Throttling location update for order {} (updated < {}ms ago)", orderId, MIN_UPDATE_INTERVAL_MS);
                return buildTrackingResponse(order, existing);
            }

            existing.setLatitude(request.getLatitude());
            existing.setLongitude(request.getLongitude());
            existing.setAccuracy(request.getAccuracy());
            existing.setDeliveryPartner(currentPartner);
            existing.setUpdatedAt(now);
            DeliveryLocation saved = deliveryLocationRepository.save(existing);
            return buildTrackingResponse(order, saved);
        } else {
            DeliveryLocation newLocation = DeliveryLocation.builder()
                    .order(order)
                    .deliveryPartner(currentPartner)
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .accuracy(request.getAccuracy())
                    .build();
            newLocation.setCreatedAt(now);
            newLocation.setUpdatedAt(now);
            DeliveryLocation saved = deliveryLocationRepository.save(newLocation);
            return buildTrackingResponse(order, saved);
        }
    }

    private void validateCoordinates(LocationUpdateRequest request) {
        if (request == null || request.getLatitude() == null || request.getLongitude() == null) {
            throw new IllegalArgumentException("Latitude and longitude must not be null");
        }

        double lat = request.getLatitude();
        double lon = request.getLongitude();

        if (Double.isNaN(lat) || Double.isInfinite(lat) || lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude must be a finite number between -90 and 90");
        }

        if (Double.isNaN(lon) || Double.isInfinite(lon) || lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("Longitude must be a finite number between -180 and 180");
        }

        if (request.getAccuracy() != null) {
            double acc = request.getAccuracy();
            if (Double.isNaN(acc) || Double.isInfinite(acc) || acc < 0.0) {
                throw new IllegalArgumentException("Accuracy must be a finite, non-negative number");
            }
        }
    }

    private void validateOrderStatusForTracking(Order order) {
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.ASSIGNED && status != OrderStatus.OUT_FOR_DELIVERY) {
            throw new IllegalStateException("Live location updates are not accepted for order in status: " + status);
        }
    }

    private void validateTrackingReadAccess(Order order) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Authentication required to access tracking");
        }

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return;
        }

        boolean isDeliveryPartner = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DELIVERY_PARTNER"));
        if (isDeliveryPartner) {
            DeliveryPartner partner = deliveryPartnerRepository.findByUserEmail(auth.getName())
                    .orElseThrow(() -> new AccessDeniedException("Delivery partner profile not found"));
            if (order.getDeliveryPartnerId() != null && order.getDeliveryPartnerId().equals(partner.getId())) {
                return;
            }
            throw new AccessDeniedException("You are not authorized to track this order");
        }

        // Customer access: current user's email must match order customerEmail
        String currentEmail = auth.getName();
        if (order.getCustomerEmail() == null || !order.getCustomerEmail().equalsIgnoreCase(currentEmail)) {
            throw new AccessDeniedException("You are not authorized to track this order");
        }
    }

    private DeliveryPartner getAuthenticatedDeliveryPartner() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Authentication required for delivery operations");
        }

        String email = auth.getName();
        return deliveryPartnerRepository.findByUserEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Delivery partner profile not found for user: " + email));
    }

    private TrackingResponse buildTrackingResponse(Order order, DeliveryLocation location) {
        boolean trackingActive = order.getStatus() == OrderStatus.ASSIGNED || order.getStatus() == OrderStatus.OUT_FOR_DELIVERY;

        TrackingResponse.TrackingResponseBuilder builder = TrackingResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .trackingActive(trackingActive)
                .customerLatitude(order.getCustomerLatitude())
                .customerLongitude(order.getCustomerLongitude())
                .customerAddress(order.getCustomerAddress())
                .customerLandmark(order.getCustomerLandmark())
                .meetAtGate(order.isMeetAtGate())
                .deliveryPartnerId(order.getDeliveryPartnerId())
                .deliveryPartnerName(order.getDeliveryPartnerName())
                .deliveryPartnerPhone(order.getDeliveryPartnerPhone());

        if (location != null) {
            builder.deliveryLatitude(location.getLatitude())
                    .deliveryLongitude(location.getLongitude())
                    .accuracy(location.getAccuracy())
                    .locationUpdatedAt(location.getUpdatedAt());
        }

        return builder.build();
    }
}
