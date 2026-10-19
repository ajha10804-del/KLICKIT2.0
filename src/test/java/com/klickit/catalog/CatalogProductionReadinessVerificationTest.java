package com.klickit.catalog;

import com.klickit.catalog.component.CatalogDataSeeder;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderItem;
import com.klickit.order.entity.OrderStatus;
import com.klickit.order.repository.OrderRepository;
import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class CatalogProductionReadinessVerificationTest {

    @Autowired
    private CatalogDataSeeder catalogDataSeeder;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @Transactional
    @DisplayName("Verification: End-to-end catalog replacement preserves UUIDs, inventory, and historical orders while updating prices and images")
    void verifyCatalogReplacementEndToEnd() {
        // Step 1: Simulate existing production database state with 246 baseline products
        // where images are null, old prices are set, and stock has been reduced by real customer orders
        Product maggiBaseline = productRepository.save(Product.builder()
                .name("Maggi Masala Noodles 70 g")
                .category("Instant Food")
                .price(new BigDecimal("15.00")) // Old price
                .stock(14) // Reduced live inventory
                .active(true)
                .imageUrl(null)
                .build());

        Product yippeeBaseline = productRepository.save(Product.builder()
                .name("Yippee Magic Masala Noodles 280 g")
                .category("Instant Food")
                .price(new BigDecimal("48.00")) // Old price
                .stock(22) // Reduced live inventory
                .active(true)
                .imageUrl(null)
                .build());

        Product attaBaseline = productRepository.save(Product.builder()
                .name("Aashirvaad Shudh Chakki Atta 5 kg")
                .category("Staples")
                .price(new BigDecimal("210.00"))
                .stock(7) // Reduced live inventory
                .active(true)
                .imageUrl(null) // Staples item with no image in new feed
                .build());

        UUID maggiIdBefore = maggiBaseline.getId();
        UUID yippeeIdBefore = yippeeBaseline.getId();
        UUID attaIdBefore = attaBaseline.getId();

        // Step 2: Create a historical order referencing Maggi at the old price (15.00)
        Order historicalOrder = Order.builder()
                .customerName("Rahul Sharma")
                .customerPhone("9876543210")
                .customerAddress("123, Scheme 78, Vijay Nagar, Indore")
                .totalAmount(new BigDecimal("30.00"))
                .status(OrderStatus.DELIVERED)
                .build();

        OrderItem historicalItem = OrderItem.builder()
                .productId(maggiIdBefore)
                .productName("Maggi Masala Noodles 70 g")
                .quantity(2)
                .price(new BigDecimal("15.00")) // Snapshot price at purchase time
                .build();
        historicalOrder.addItem(historicalItem);
        Order savedOrder = orderRepository.save(historicalOrder);

        // Step 3: Run catalog synchronizer with the updated catalog CSV
        catalogDataSeeder.run(new DefaultApplicationArguments(new String[0]));

        // Step 4: Verify catalog replacement metrics
        List<Product> allProducts = productRepository.findAll();
        assertThat(allProducts).hasSize(246);

        // Verify image coverage
        long productsWithImage = allProducts.stream()
                .filter(p -> p.getImageUrl() != null && !p.getImageUrl().isBlank())
                .count();
        long productsWithoutImage = allProducts.stream()
                .filter(p -> p.getImageUrl() == null || p.getImageUrl().isBlank())
                .count();

        assertThat(productsWithImage).isEqualTo(178);
        assertThat(productsWithoutImage).isEqualTo(68); // 66 Staples + 2 cleared corrupted shampoos

        // Step 5: Verify in-place updates, UUID preservation, and inventory protection
        Product maggiAfter = productRepository.findByNameIgnoreCase("Maggi Masala Noodles 70 g").orElseThrow();
        assertThat(maggiAfter.getId()).isEqualTo(maggiIdBefore); // UUID MUST be preserved!
        assertThat(maggiAfter.getPrice()).isEqualByComparingTo(new BigDecimal("18.00")); // Updated from 15.00 to 18.00
        assertThat(maggiAfter.getStock()).isEqualTo(14); // Live stock MUST remain 14!
        assertThat(maggiAfter.getImageUrl()).isNotBlank();
        assertThat(maggiAfter.getImageUrl()).startsWith("https://");

        Product yippeeAfter = productRepository.findByNameIgnoreCase("Yippee Magic Masala Noodles 280 g").orElseThrow();
        assertThat(yippeeAfter.getId()).isEqualTo(yippeeIdBefore); // UUID MUST be preserved!
        assertThat(yippeeAfter.getPrice()).isEqualByComparingTo(new BigDecimal("58.00")); // Updated from 48.00 to 58.00
        assertThat(yippeeAfter.getStock()).isEqualTo(22); // Live stock MUST remain 22!

        Product attaAfter = productRepository.findByNameIgnoreCase("Aashirvaad Shudh Chakki Atta 5 kg").orElseThrow();
        assertThat(attaAfter.getId()).isEqualTo(attaIdBefore); // UUID MUST be preserved!
        assertThat(attaAfter.getStock()).isEqualTo(7); // Live stock MUST remain 7!
        assertThat(attaAfter.getImageUrl()).isNull(); // Clean placeholder fallback in frontend

        // Step 6: Verify historical order and item preservation
        Order reloadedOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
        assertThat(reloadedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(reloadedOrder.getItems()).hasSize(1);

        OrderItem reloadedItem = reloadedOrder.getItems().get(0);
        assertThat(reloadedItem.getProductId()).isEqualTo(maggiIdBefore);
        assertThat(reloadedItem.getProductName()).isEqualTo("Maggi Masala Noodles 70 g");
        assertThat(reloadedItem.getPrice()).isEqualByComparingTo(new BigDecimal("15.00")); // Historical price UNCHANGED!

        // Step 7: Verify idempotency - running synchronizer again causes 0 errors and preserves all state
        catalogDataSeeder.run(new DefaultApplicationArguments(new String[0]));

        List<Product> productsAfterSecondRun = productRepository.findAll();
        assertThat(productsAfterSecondRun).hasSize(246);
        Product maggiSecondRun = productRepository.findByNameIgnoreCase("Maggi Masala Noodles 70 g").orElseThrow();
        assertThat(maggiSecondRun.getId()).isEqualTo(maggiIdBefore);
        assertThat(maggiSecondRun.getPrice()).isEqualByComparingTo(new BigDecimal("18.00"));
        assertThat(maggiSecondRun.getStock()).isEqualTo(14);
    }
}
