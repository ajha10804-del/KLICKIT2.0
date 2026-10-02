package com.klickit.order.dto;

import com.klickit.order.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class OrderItemResponse {

    private final String productName;
    private final int quantity;
    private final BigDecimal price;
    private final BigDecimal lineTotal;

    public static OrderItemResponse from(OrderItem item) {
        return OrderItemResponse.builder()
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .lineTotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .build();
    }
}
