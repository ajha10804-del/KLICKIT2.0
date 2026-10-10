package com.klickit.catalog.service;

import com.klickit.catalog.dto.CatalogImportOptions;
import com.klickit.catalog.dto.CatalogImportResult;
import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CatalogImportService {

    private final ProductRepository productRepository;

    @Transactional
    public CatalogImportResult importFromCsv(InputStream inputStream, CatalogImportOptions options) {
        CatalogImportOptions importOptions = options != null ? options : CatalogImportOptions.defaults();
        CatalogImportResult result = new CatalogImportResult();

        if (inputStream == null) {
            result.addError("Input stream is null");
            return result;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                result.addError("CSV file is empty");
                return result;
            }

            Map<String, Integer> headerIndexMap = parseHeaderIndices(headerLine);
            if (!headerIndexMap.containsKey("name") || !headerIndexMap.containsKey("category") || !headerIndexMap.containsKey("price")) {
                result.addError("Required headers (name, category, price) missing in CSV header: " + headerLine);
                return result;
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                result.setTotalProcessed(result.getTotalProcessed() + 1);

                List<String> tokens = parseCsvLine(line);
                String name = getColumnValue(tokens, headerIndexMap, "name");
                String category = getColumnValue(tokens, headerIndexMap, "category");
                String priceStr = getColumnValue(tokens, headerIndexMap, "price");
                String quantity = getColumnValue(tokens, headerIndexMap, "quantity");
                String description = getColumnValue(tokens, headerIndexMap, "description");
                String stockStr = getColumnValue(tokens, headerIndexMap, "stock");
                String imageUrl = getColumnValue(tokens, headerIndexMap, "image_url");
                if (imageUrl.isEmpty()) {
                    imageUrl = getColumnValue(tokens, headerIndexMap, "imageurl");
                }

                if (name.isEmpty() || category.isEmpty() || priceStr.isEmpty()) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    result.addError(String.format("Line %d: Name, category, or price is blank: %s", lineNumber, line));
                    continue;
                }

                BigDecimal price;
                try {
                    price = new BigDecimal(priceStr);
                    if (price.compareTo(BigDecimal.ZERO) < 0) {
                        result.setSkippedCount(result.getSkippedCount() + 1);
                        result.addError(String.format("Line %d: Price cannot be negative [%s]", lineNumber, priceStr));
                        continue;
                    }
                } catch (NumberFormatException e) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    result.addError(String.format("Line %d: Invalid price format [%s]", lineNumber, priceStr));
                    continue;
                }

                Integer feedStock = null;
                if (!stockStr.isEmpty()) {
                    try {
                        feedStock = Integer.parseInt(stockStr);
                    } catch (NumberFormatException e) {
                        result.addError(String.format("Line %d: Invalid stock format [%s], using default if new", lineNumber, stockStr));
                    }
                }

                String finalDescription = !description.isEmpty() ? description : (!quantity.isEmpty() ? "Pack size: " + quantity : null);

                // Upsert logic matching natural key (name, case-insensitive)
                Optional<Product> existingOpt = productRepository.findByNameIgnoreCase(name);
                if (existingOpt.isPresent()) {
                    Product existing = existingOpt.get();
                    boolean metadataChanged = false;

                    // 1. Metadata updates (in-place)
                    boolean metaFieldChanged = false;
                    if (!category.equalsIgnoreCase(existing.getCategory())) {
                        existing.setCategory(category);
                        metaFieldChanged = true;
                    }
                    if (finalDescription != null && !finalDescription.equals(existing.getDescription())) {
                        existing.setDescription(finalDescription);
                        metaFieldChanged = true;
                    }
                    if (!imageUrl.isEmpty() && !imageUrl.equals(existing.getImageUrl())) {
                        existing.setImageUrl(imageUrl);
                        metaFieldChanged = true;
                    }
                    if (metaFieldChanged) {
                        result.setMetadataUpdatedCount(result.getMetadataUpdatedCount() + 1);
                        metadataChanged = true;
                    }

                    // 2. Price update (if enabled)
                    if (importOptions.isUpdatePrices() && price.compareTo(existing.getPrice()) != 0) {
                        existing.setPrice(price);
                        result.setPricesUpdatedCount(result.getPricesUpdatedCount() + 1);
                        metadataChanged = true;
                    }

                    // 3. Stock update (STRICT OPT-IN ONLY)
                    if (importOptions.isUpdateStock() && feedStock != null && feedStock != existing.getStock()) {
                        existing.setStock(feedStock);
                        result.setStockUpdatedCount(result.getStockUpdatedCount() + 1);
                        metadataChanged = true;
                    }

                    if (metadataChanged) {
                        productRepository.save(existing);
                    }
                } else {
                    // New product insertion
                    int initialStock = feedStock != null ? feedStock : 50;
                    Product newProduct = Product.builder()
                            .name(name)
                            .category(category)
                            .price(price)
                            .description(finalDescription)
                            .imageUrl(!imageUrl.isEmpty() ? imageUrl : null)
                            .active(importOptions.isActivateNewProducts())
                            .stock(initialStock)
                            .build();

                    productRepository.save(newProduct);
                    result.setInsertedCount(result.getInsertedCount() + 1);
                }
            }
        } catch (Exception e) {
            log.error("Failed to import catalog from CSV: {}", e.getMessage(), e);
            result.addError("Import failed with exception: " + e.getMessage());
        }

        log.info("Catalog import complete: processed={}, inserted={}, metadataUpdated={}, pricesUpdated={}, stockUpdated={}, skipped={}, errors={}",
                result.getTotalProcessed(), result.getInsertedCount(), result.getMetadataUpdatedCount(),
                result.getPricesUpdatedCount(), result.getStockUpdatedCount(), result.getSkippedCount(), result.getErrors().size());

        return result;
    }

    private Map<String, Integer> parseHeaderIndices(String headerLine) {
        Map<String, Integer> headerMap = new HashMap<>();
        List<String> tokens = parseCsvLine(headerLine);
        for (int i = 0; i < tokens.size(); i++) {
            String col = tokens.get(i).toLowerCase().replaceAll("[^a-z0-9_]", "");
            headerMap.put(col, i);
        }
        return headerMap;
    }

    private String getColumnValue(List<String> tokens, Map<String, Integer> headerMap, String key) {
        Integer idx = headerMap.get(key);
        if (idx != null && idx < tokens.size()) {
            return tokens.get(idx).trim();
        }
        return "";
    }

    public List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        values.add(sb.toString().trim());
        return values;
    }
}
