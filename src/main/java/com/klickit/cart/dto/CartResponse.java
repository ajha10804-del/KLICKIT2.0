package com.klickit.cart.dto;

import com.klickit.cart.entity.Cart;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class CartResponse {

    private final UUID id;
    private final String sessionId;
    private final List<CartItemResponse> items;
    private final int itemCount;
    private final BigDecimal subtotal;

    public static CartResponse from(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(CartItemResponse::from)
                .toList();

        int itemCount = cart.getItems().stream()
                .mapToInt(item -> item.getQuantity())
                .sum();

        BigDecimal subtotal = itemResponses.stream()
                .map(CartItemResponse::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartResponse.builder()
                .id(cart.getId())
                .sessionId(cart.getSessionId())
                .items(itemResponses)
                .itemCount(itemCount)
                .subtotal(subtotal)
                .build();
    }
}
