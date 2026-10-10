package com.klickit.catalog.service;

import com.klickit.cart.dto.AddToCartRequest;
import com.klickit.cart.entity.Cart;
import com.klickit.cart.entity.CartItem;
import com.klickit.cart.repository.CartRepository;
import com.klickit.cart.service.CartService;
import com.klickit.catalog.dto.CatalogImportOptions;
import com.klickit.catalog.dto.CatalogImportResult;
import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class CatalogImportServiceTest {

    @Autowired
    private CatalogImportService catalogImportService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartRepository.deleteAll();
        productRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        cartRepository.deleteAll();
        productRepository.deleteAll();
    }

    private InputStream createCsvStream(String csvContent) {
        return new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Requirement 1: Idempotency & In-Place Update — Importing feed twice preserves product UUID without duplication")
    void importingSameFeedTwicePreservesUuidWithoutDuplication() {
        String csv = """
                name,category,price,quantity
                Whole Farm Milk 1L,Dairy & Eggs,60.00,1 L
                Artisan Bread 400g,Bakery,45.00,400 g
                """;

        // First import
        CatalogImportResult result1 = catalogImportService.importFromCsv(createCsvStream(csv), CatalogImportOptions.defaults());
        assertThat(result1.getInsertedCount()).isEqualTo(2);
        assertThat(result1.getTotalProcessed()).isEqualTo(2);
        assertThat(productRepository.count()).isEqualTo(2);

        Product initialMilk = productRepository.findByNameIgnoreCase("Whole Farm Milk 1L").orElseThrow();
        UUID initialMilkId = initialMilk.getId();

        // Second import (exact same content)
        CatalogImportResult result2 = catalogImportService.importFromCsv(createCsvStream(csv), CatalogImportOptions.defaults());
        assertThat(result2.getInsertedCount()).isEqualTo(0);
        assertThat(result2.getMetadataUpdatedCount()).isEqualTo(0);
        assertThat(result2.getPricesUpdatedCount()).isEqualTo(0);
        assertThat(productRepository.count()).isEqualTo(2);

        Product reloadedMilk = productRepository.findByNameIgnoreCase("Whole Farm Milk 1L").orElseThrow();
        assertThat(reloadedMilk.getId()).isEqualTo(initialMilkId);
    }

    @Test
    @DisplayName("Requirement 2: Default Inventory Safety Guard — updateStock=false leaves existing live stock untouched")
    void defaultImportLeavesExistingStockUntouched() {
        // Pre-condition: existing product with stock reduced to 5 due to customer purchases
        Product product = productRepository.save(Product.builder()
                .name("Organic Almond Milk")
                .category("Dairy & Eggs")
                .price(new BigDecimal("120.00"))
                .stock(5) // Reduced from 50 to 5
                .active(true)
                .build());

        // Feed specifies stock = 100
        String csv = """
                name,category,price,stock,description
                Organic Almond Milk,Dairy & Eggs,120.00,100,Pure California almonds
                """;

        CatalogImportResult result = catalogImportService.importFromCsv(createCsvStream(csv), CatalogImportOptions.defaults());

        assertThat(result.getStockUpdatedCount()).isEqualTo(0);
        assertThat(result.getMetadataUpdatedCount()).isEqualTo(1); // description updated

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getStock()).isEqualTo(5); // Stock MUST remain 5!
        assertThat(reloaded.getDescription()).isEqualTo("Pure California almonds");
    }

    @Test
    @DisplayName("Requirement 3: Explicit Stock Opt-In — updateStock=true updates stock to feed value")
    void explicitStockOptInUpdatesStock() {
        Product product = productRepository.save(Product.builder()
                .name("Organic Almond Milk")
                .category("Dairy & Eggs")
                .price(new BigDecimal("120.00"))
                .stock(5)
                .active(true)
                .build());

        String csv = """
                name,category,price,stock,description
                Organic Almond Milk,Dairy & Eggs,120.00,100,Pure California almonds
                """;

        CatalogImportOptions optInOptions = CatalogImportOptions.builder()
                .updatePrices(true)
                .updateStock(true) // Explicit opt-in!
                .build();

        CatalogImportResult result = catalogImportService.importFromCsv(createCsvStream(csv), optInOptions);

        assertThat(result.getStockUpdatedCount()).isEqualTo(1);

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getStock()).isEqualTo(100);
    }

    @Test
    @DisplayName("Requirement 4: Price Update Reporting — updates price and explicitly reports pricesUpdatedCount")
    void priceUpdateReportingTracksChanges() {
        Product product = productRepository.save(Product.builder()
                .name("Greek Yogurt 200g")
                .category("Dairy & Eggs")
                .price(new BigDecimal("50.00"))
                .stock(40)
                .active(true)
                .build());

        String csv = """
                name,category,price,quantity
                Greek Yogurt 200g,Dairy & Eggs,55.00,200 g
                """;

        CatalogImportResult result = catalogImportService.importFromCsv(createCsvStream(csv), CatalogImportOptions.defaults());

        assertThat(result.getPricesUpdatedCount()).isEqualTo(1);

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getPrice()).isEqualByComparingTo(new BigDecimal("55.00"));
    }

    @Test
    @DisplayName("Requirement 5: New Product Ingestion — new product in feed inserted with active=true and initial stock")
    void newProductIngestedWithActiveAndStock() {
        String csv = """
                name,category,price,stock,description
                Dark Chocolate Bar,Snacks & Drinks,90.00,75,70% cocoa rich dark chocolate
                """;

        CatalogImportResult result = catalogImportService.importFromCsv(createCsvStream(csv), CatalogImportOptions.defaults());

        assertThat(result.getInsertedCount()).isEqualTo(1);
        Product inserted = productRepository.findByNameIgnoreCase("Dark Chocolate Bar").orElseThrow();
        assertThat(inserted.isActive()).isTrue();
        assertThat(inserted.getStock()).isEqualTo(75);
        assertThat(inserted.getPrice()).isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(inserted.getDescription()).isEqualTo("70% cocoa rich dark chocolate");
    }

    @Test
    @DisplayName("Requirement 6: Cart Integrity Preservation — CartItem remains valid and linked after catalog re-import")
    void cartIntegrityPreservedAfterReimport() {
        // 1. Initial product import
        String csvInitial = """
                name,category,price,quantity
                Fresh Strawberries 250g,Fruits & Veg,110.00,250 g
                """;
        catalogImportService.importFromCsv(createCsvStream(csvInitial), CatalogImportOptions.defaults());
        Product strawberry = productRepository.findByNameIgnoreCase("Fresh Strawberries 250g").orElseThrow();

        // 2. Customer adds item to cart
        String sessionId = "user-cart-session-123";
        AddToCartRequest addRequest = new AddToCartRequest(sessionId, strawberry.getId(), 2);
        cartService.addToCart(addRequest);

        com.klickit.cart.dto.CartResponse cartBefore = cartService.getCart(sessionId);
        assertThat(cartBefore.getItems()).hasSize(1);
        assertThat(cartBefore.getItems().get(0).getProductId()).isEqualTo(strawberry.getId());

        // 3. Re-import catalog with price and description update
        String csvUpdated = """
                name,category,price,quantity
                Fresh Strawberries 250g,Fruits & Veg,115.00,250 g pack
                """;
        catalogImportService.importFromCsv(createCsvStream(csvUpdated), CatalogImportOptions.defaults());

        // 4. Verify cart still references the exact same product UUID and remains fully valid
        com.klickit.cart.dto.CartResponse cartAfter = cartService.getCart(sessionId);
        assertThat(cartAfter.getItems()).hasSize(1);
        com.klickit.cart.dto.CartItemResponse cartItem = cartAfter.getItems().get(0);
        assertThat(cartItem.getProductId()).isEqualTo(strawberry.getId());

        // Live product still exists in DB with matching ID
        Product reloadedStrawberry = productRepository.findById(cartItem.getProductId()).orElseThrow();
        assertThat(reloadedStrawberry.getId()).isEqualTo(strawberry.getId());
        assertThat(reloadedStrawberry.getPrice()).isEqualByComparingTo(new BigDecimal("115.00"));
    }

    @Test
    @DisplayName("Requirement 7: Malformed Row Tolerance — Invalid price recorded in errors without halting processing of valid rows")
    void malformedRowRecordedInErrorsWithoutHaltingValidRows() {
        String csv = """
                name,category,price,quantity
                Valid Product 1,Dairy & Eggs,50.00,100 g
                Malformed Price Product,Bakery,INVALID_PRICE,200 g
                Valid Product 2,Instant Food,25.00,70 g
                """;

        CatalogImportResult result = catalogImportService.importFromCsv(createCsvStream(csv), CatalogImportOptions.defaults());

        assertThat(result.getTotalProcessed()).isEqualTo(3);
        assertThat(result.getInsertedCount()).isEqualTo(2);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0)).contains("Invalid price format [INVALID_PRICE]");

        assertThat(productRepository.findByNameIgnoreCase("Valid Product 1")).isPresent();
        assertThat(productRepository.findByNameIgnoreCase("Valid Product 2")).isPresent();
        assertThat(productRepository.findByNameIgnoreCase("Malformed Price Product")).isNotPresent();
    }
}
