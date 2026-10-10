package com.klickit.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogImportResult {

    private int totalProcessed;
    private int insertedCount;
    private int metadataUpdatedCount;
    private int pricesUpdatedCount;
    private int stockUpdatedCount;
    private int skippedCount;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public void addError(String error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
    }
}
