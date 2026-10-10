package com.klickit.product.dto;

import com.klickit.product.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ProductResponse {

    private final UUID id;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final String imageUrl;
    private final String category;
    private final boolean active;
    private final int stock;
    private final Instant createdAt;
    private final Instant updatedAt;

    public static ProductResponse from(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .imageUrl(product.getImageUrl())
                .category(product.getCategory())
                .active(product.isActive())
                .stock(product.getStock())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
