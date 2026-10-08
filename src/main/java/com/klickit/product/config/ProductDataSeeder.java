package com.klickit.product.config;

import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
@ConditionalOnProperty(name = "klickit.seed.demo-products", havingValue = "true")
public class ProductDataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedProducts();
    }

    public void seedProducts() {
        List<SeedProduct> seeds = List.of(
                new SeedProduct("Maggi", "Classic instant masala noodles", new BigDecimal("14.00"), "Groceries"),
                new SeedProduct("Coke", "Chilled carbonated cola soft drink", new BigDecimal("40.00"), "Beverages"),
                new SeedProduct("Sprite", "Refreshing lemon-lime flavored beverage", new BigDecimal("40.00"), "Beverages"),
                new SeedProduct("Notebook", "Ruled exercise notebook 120 pages", new BigDecimal("45.00"), "Stationery"),
                new SeedProduct("Pen", "Smooth writing blue ballpoint pen", new BigDecimal("10.00"), "Stationery"),
                new SeedProduct("Toothpaste", "Anticavity fluoride toothpaste 120g", new BigDecimal("75.00"), "Personal Care")
        );

        for (SeedProduct seed : seeds) {
            if (!productRepository.existsByNameIgnoreCase(seed.name())) {
                Product product = Product.builder()
                        .name(seed.name())
                        .description(seed.description())
                        .price(seed.price())
                        .category(seed.category())
                        .active(true)
                        .build();
                productRepository.save(product);
                log.info("Seeded product: {}", seed.name());
            }
        }
    }

    private record SeedProduct(String name, String description, BigDecimal price, String category) {}
}
