package com.klickit.catalog.component;

import com.klickit.catalog.dto.CatalogImportOptions;
import com.klickit.catalog.dto.CatalogImportResult;
import com.klickit.catalog.service.CatalogImportService;
import com.klickit.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

@Component
@Slf4j
@RequiredArgsConstructor
@Order(2)
public class CatalogDataSeeder implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final CatalogImportService catalogImportService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ClassPathResource resource = new ClassPathResource("data/catalog.csv");
        if (!resource.exists()) {
            log.warn("Catalog CSV resource 'data/catalog.csv' not found. Skipping catalog synchronization.");
            return;
        }

        try (InputStream is = resource.getInputStream()) {
            CatalogImportOptions options = CatalogImportOptions.builder()
                    .updatePrices(true)
                    .updateStock(false) // Protect live inventory
                    .activateNewProducts(true)
                    .build();

            CatalogImportResult result = catalogImportService.importFromCsv(is, options);
            log.info("Catalog synchronization finished: processed={}, inserted={}, metadataUpdated={}, pricesUpdated={}, skipped={}, errors={}",
                    result.getTotalProcessed(), result.getInsertedCount(), result.getMetadataUpdatedCount(),
                    result.getPricesUpdatedCount(), result.getSkippedCount(), result.getErrors().size());
        } catch (Exception e) {
            log.error("Failed to synchronize product catalog from CSV: [type={}]", e.getClass().getName());
        }
    }
}
