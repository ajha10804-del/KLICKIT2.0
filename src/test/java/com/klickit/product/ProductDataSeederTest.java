package com.klickit.product;

import com.klickit.product.config.ProductDataSeeder;
import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDataSeederTest {

    @Mock
    private ProductRepository productRepository;

    private ProductDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new ProductDataSeeder(productRepository);
    }

    @Test
    @DisplayName("Seeds all 6 products when repository is empty")
    void seedsAllSixProductsWhenEmpty() {
        when(productRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);

        seeder.seedProducts();

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(6)).save(captor.capture());

        List<String> seededNames = captor.getAllValues().stream()
                .map(Product::getName)
                .toList();

        assertThat(seededNames).containsExactlyInAnyOrder(
                "Maggi",
                "Coke",
                "Sprite",
                "Notebook",
                "Pen",
                "Toothpaste"
        );
    }

    @Test
    @DisplayName("Idempotent: Does not create duplicate products when products already exist")
    void doesNotDuplicateProductsWhenAlreadyExist() {
        when(productRepository.existsByNameIgnoreCase(anyString())).thenReturn(true);

        seeder.seedProducts();

        verify(productRepository, never()).save(any(Product.class));
    }
}
