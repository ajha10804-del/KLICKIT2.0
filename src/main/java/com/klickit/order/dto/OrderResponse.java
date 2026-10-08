package com.klickit.order.dto;

import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
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
    private final String customerEmail;
    private final Instant deadline;
    private final BigDecimal totalAmount;
    private final BigDecimal deliveryFee;
    private final BigDecimal subtotal;
    private final UUID deliveryPartnerId;
    private final String deliveryPartnerName;
    private final String deliveryPartnerPhone;
    private final OrderStatus status;
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

        return OrderResponse.builder()
                .id(order.getId())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .customerAddress(order.getCustomerAddress())
                .customerEmail(order.getCustomerEmail())
                .deadline(order.getDeadline())
                .totalAmount(total)
                .deliveryFee(fee)
                .subtotal(subtotal)
                .deliveryPartnerId(order.getDeliveryPartnerId())
                .deliveryPartnerName(order.getDeliveryPartnerName())
                .deliveryPartnerPhone(order.getDeliveryPartnerPhone())
                .status(order.getStatus())
                .notificationSent(order.isNotificationSent())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
