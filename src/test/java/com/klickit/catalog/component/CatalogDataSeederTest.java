package com.klickit.catalog.component;

import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogDataSeederTest {

    @Mock
    private ProductRepository productRepository;

    private CatalogDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new CatalogDataSeeder(productRepository);
    }

    @Test
    @DisplayName("Case A (Empty DB): When productRepository.count() == 0, seeds exactly 246 products from CSV")
    void seedsAllProductsWhenRepositoryIsEmpty() {
        when(productRepository.count()).thenReturn(0L);

        seeder.run(new DefaultApplicationArguments(new String[0]));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());

        List<Product> seededProducts = captor.getValue();
        assertThat(seededProducts).hasSize(246);

        // Check first product attributes matching CSV
        Product firstProduct = seededProducts.get(0);
        assertThat(firstProduct.getName()).isEqualTo("Maggi Masala Noodles 70 g");
        assertThat(firstProduct.getCategory()).isEqualTo("Instant Food");
        assertThat(firstProduct.getPrice()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(firstProduct.getDescription()).isEqualTo("Pack size: 70 g");
        assertThat(firstProduct.isActive()).isTrue();
    }

    @Test
    @DisplayName("Case B (Already Populated): When productRepository.count() > 0, skips seeding and never calls saveAll")
    void skipsSeedingWhenRepositoryAlreadyHasProducts() {
        when(productRepository.count()).thenReturn(10L);

        seeder.run(new DefaultApplicationArguments(new String[0]));

        verify(productRepository, never()).saveAll(anyList());
    }
}
