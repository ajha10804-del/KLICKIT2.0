package com.klickit.order.dto;

import com.klickit.order.entity.DrivingDistanceStatus;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class OrderResponse {

    private final UUID id;
    private final String customerName;
    private final String customerPhone;
    private final String customerAddress;
    private final Double customerLatitude;
    private final Double customerLongitude;
    private final String customerLandmark;
    private final boolean meetAtGate;
    private final String customerEmail;
    private final Instant deadline;
    private final BigDecimal totalAmount;
    private final BigDecimal deliveryFee;
    private final BigDecimal subtotal;
    private final UUID deliveryPartnerId;
    private final String deliveryPartnerName;
    private final String deliveryPartnerPhone;
    private final OrderStatus status;
    private final Integer drivingDistanceMeters;
    private final BigDecimal drivingDistanceKm;
    private final DrivingDistanceStatus drivingDistanceStatus;
    private final Boolean drivingDistanceEligible;
    private final boolean notificationSent;
    private final List<OrderItemResponse> items;
    private final Instant createdAt;

    public static OrderResponse from(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems() != null
                ? order.getItems().stream().map(OrderItemResponse::from).toList()
                : List.of();

        BigDecimal fee = order.getDeliveryFee() != null ? order.getDeliveryFee() : BigDecimal.ZERO;
        BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal subtotal = total.subtract(fee);

        Integer distanceMeters = order.getDrivingDistanceMeters();
        BigDecimal distanceKm = null;
        if (distanceMeters != null) {
            distanceKm = BigDecimal.valueOf(distanceMeters)
                    .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
        }

        DrivingDistanceStatus distanceStatus = order.getDrivingDistanceStatus() != null
                ? order.getDrivingDistanceStatus()
                : DrivingDistanceStatus.UNAVAILABLE;

        boolean eligible = distanceStatus == DrivingDistanceStatus.ELIGIBLE
                && distanceMeters != null
                && distanceMeters <= 5000;

        return OrderResponse.builder()
                .id(order.getId())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .customerAddress(order.getCustomerAddress())
                .customerLatitude(order.getCustomerLatitude())
                .customerLongitude(order.getCustomerLongitude())
                .customerLandmark(order.getCustomerLandmark())
                .meetAtGate(order.isMeetAtGate())
                .customerEmail(order.getCustomerEmail())
                .deadline(order.getDeadline())
                .totalAmount(total)
                .deliveryFee(fee)
                .subtotal(subtotal)
                .deliveryPartnerId(order.getDeliveryPartnerId())
                .deliveryPartnerName(order.getDeliveryPartnerName())
                .deliveryPartnerPhone(order.getDeliveryPartnerPhone())
                .status(order.getStatus())
                .drivingDistanceMeters(distanceMeters)
                .drivingDistanceKm(distanceKm)
                .drivingDistanceStatus(distanceStatus)
                .drivingDistanceEligible(eligible)
                .notificationSent(order.isNotificationSent())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
