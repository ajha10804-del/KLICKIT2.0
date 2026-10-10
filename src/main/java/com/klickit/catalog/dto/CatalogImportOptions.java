package com.klickit.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogImportOptions {

    @Builder.Default
    private boolean updatePrices = true;

    @Builder.Default
    private boolean updateStock = false; // Strictly opt-in: false by default to protect live inventory

    @Builder.Default
    private boolean activateNewProducts = true;

    public static CatalogImportOptions defaults() {
        return CatalogImportOptions.builder()
                .updatePrices(true)
                .updateStock(false)
                .activateNewProducts(true)
                .build();
    }
}
