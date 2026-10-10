package com.klickit.order.entity;

import com.klickit.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order extends BaseEntity {

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerPhone;

    @Column(name = "customer_address")
    private String customerAddress;

    @Column(name = "customer_latitude")
    private Double customerLatitude;

    @Column(name = "customer_longitude")
    private Double customerLongitude;

    @Column(name = "customer_landmark")
    private String customerLandmark;

    @Builder.Default
    @Column(name = "meet_at_gate", nullable = false)
    private boolean meetAtGate = false;

    private String customerEmail;

    private Instant deadline;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Builder.Default
    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    private UUID deliveryPartnerId;

    private String deliveryPartnerName;

    private String deliveryPartnerPhone;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PLACED;

    @Column(name = "driving_distance_meters")
    private Integer drivingDistanceMeters;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "driving_distance_status", nullable = false, length = 32)
    private DrivingDistanceStatus drivingDistanceStatus = DrivingDistanceStatus.UNAVAILABLE;

    @Column(name = "driving_distance_destination_lat")
    private Double drivingDistanceDestinationLat;

    @Column(name = "driving_distance_destination_lng")
    private Double drivingDistanceDestinationLng;

    @Builder.Default
    @Column(name = "notification_sent", nullable = false)
    private boolean notificationSent = false;

    @Builder.Default
    @org.hibernate.annotations.BatchSize(size = 50)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
