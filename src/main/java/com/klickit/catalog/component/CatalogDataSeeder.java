package com.klickit.catalog.component;

import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@Order(2)
public class CatalogDataSeeder implements ApplicationRunner {

    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            log.info("Product catalog already contains data (count={}). Skipping catalog CSV seeding.", productRepository.count());
            return;
        }

        ClassPathResource resource = new ClassPathResource("data/catalog.csv");
        if (!resource.exists()) {
            log.warn("Catalog CSV resource 'data/catalog.csv' not found. Skipping catalog seeding.");
            return;
        }

        List<Product> products = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                List<String> tokens = parseCsvLine(line);
                if (tokens.size() < 3) {
                    log.warn("Skipping malformed CSV line: {}", line);
                    continue;
                }

                String name = tokens.get(0).trim();
                String category = tokens.get(1).trim();
                String priceStr = tokens.get(2).trim();
                String quantity = tokens.size() > 3 ? tokens.get(3).trim() : "";

                if (name.isEmpty() || category.isEmpty() || priceStr.isEmpty()) {
                    continue;
                }

                BigDecimal price;
                try {
                    price = new BigDecimal(priceStr);
                } catch (NumberFormatException e) {
                    log.warn("Invalid price [{}] in CSV line, skipping: {}", priceStr, line);
                    continue;
                }

                String description = quantity.isEmpty() ? "" : "Pack size: " + quantity;

                // Explicit pilot opening-stock policy: 50 units per catalog product
                Product product = Product.builder()
                        .name(name)
                        .category(category)
                        .price(price)
                        .description(description)
                        .active(true)
                        .stock(50)
                        .build();

                products.add(product);
            }

            if (!products.isEmpty()) {
                productRepository.saveAll(products);
                log.info("Successfully seeded {} products from catalog CSV.", products.size());
            }
        } catch (Exception e) {
            log.error("Failed to seed product catalog from CSV: {}", e.getMessage(), e);
        }
    }

    private List<String> parseCsvLine(String line) {
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
