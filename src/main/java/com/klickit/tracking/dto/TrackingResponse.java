package com.klickit.tracking.dto;

import com.klickit.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingResponse {

    private UUID orderId;
    private OrderStatus status;
    private boolean trackingActive;
    private Double customerLatitude;
    private Double customerLongitude;
    private String customerAddress;
    private String customerLandmark;
    private boolean meetAtGate;
    private Double deliveryLatitude;
    private Double deliveryLongitude;
    private Double accuracy;
    private Instant locationUpdatedAt;
    private UUID deliveryPartnerId;
    private String deliveryPartnerName;
    private String deliveryPartnerPhone;
}
