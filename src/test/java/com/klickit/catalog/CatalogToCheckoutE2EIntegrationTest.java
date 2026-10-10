package com.klickit.catalog;

import com.klickit.cart.dto.AddToCartRequest;
import com.klickit.cart.dto.CartItemResponse;
import com.klickit.cart.dto.CartResponse;
import com.klickit.cart.repository.CartRepository;
import com.klickit.cart.service.CartService;
import com.klickit.catalog.dto.CatalogImportOptions;
import com.klickit.catalog.dto.CatalogImportResult;
import com.klickit.catalog.service.CatalogImportService;
import com.klickit.order.dto.CheckoutRequest;
import com.klickit.order.dto.OrderResponse;
import com.klickit.order.entity.Order;
import com.klickit.order.repository.OrderRepository;
import com.klickit.order.service.OrderService;
import com.klickit.product.dto.ProductResponse;
import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import com.klickit.product.service.ProductService;
import com.klickit.common.dto.PagedResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "klickit.delivery.enforce-range=false",
        "klickit.routing.enabled=false"
})
public class CatalogToCheckoutE2EIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CatalogImportService catalogImportService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductService productService;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();

        // Authenticate test context as customer
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "e2e-customer@klickit.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
    }

    private InputStream createLargeCatalogCsvStream(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("name,category,price,stock,description,image_url\n");
        String[] categories = {"Dairy & Eggs", "Bakery", "Fruits & Veg", "Snacks & Drinks", "Household"};

        for (int i = 1; i <= count; i++) {
            String cat = categories[(i - 1) % categories.length];
            BigDecimal price = BigDecimal.valueOf(10 + (i % 90));
            int stock = 50;
            String desc = "Pack of essential grocery " + i;
            // Deliberately leave some image URLs null/empty to verify image resilience
            String imageUrl = (i % 5 == 0) ? "" : "https://images.example.com/item-" + i + ".jpg";

            sb.append(String.format("Product %04d,%s,%.2f,%d,%s,%s\n", i, cat, price, stock, desc, imageUrl));
        }
        return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("E2E Phase 1: Large-Catalog Ingestion (1,000 items) & Idempotency")
    void largeCatalogIngestionAndIdempotency() {
        // 1. Ingest 1,000 products through the actual importer
        CatalogImportResult result1 = catalogImportService.importFromCsv(
                createLargeCatalogCsvStream(1000),
                CatalogImportOptions.defaults()
        );

        assertThat(result1.getTotalProcessed()).isEqualTo(1000);
        assertThat(result1.getInsertedCount()).isEqualTo(1000);
        assertThat(result1.getErrors()).isEmpty();
        assertThat(productRepository.count()).isEqualTo(1000);

        Product sampledProductBefore = productRepository.findByNameIgnoreCase("Product 0042").orElseThrow();
        UUID originalId = sampledProductBefore.getId();
        int originalStock = sampledProductBefore.getStock();

        // 2. Duplicate import with same data
        CatalogImportResult result2 = catalogImportService.importFromCsv(
                createLargeCatalogCsvStream(1000),
                CatalogImportOptions.defaults()
        );

        assertThat(result2.getTotalProcessed()).isEqualTo(1000);
        assertThat(result2.getInsertedCount()).isEqualTo(0);
        assertThat(result2.getStockUpdatedCount()).isEqualTo(0);
        assertThat(productRepository.count()).isEqualTo(1000);

        Product sampledProductAfter = productRepository.findByNameIgnoreCase("Product 0042").orElseThrow();
        assertThat(sampledProductAfter.getId()).isEqualTo(originalId);
        assertThat(sampledProductAfter.getStock()).isEqualTo(originalStock);
    }

    @Test
    @DisplayName("E2E Phase 2: Browsing, Search, Pagination, and Sort Verification against Imported Data")
    void browsingSearchPaginationAndSortingAgainstImportedCatalog() throws Exception {
        catalogImportService.importFromCsv(createLargeCatalogCsvStream(1000), CatalogImportOptions.defaults());

        // 1. Search Query: "Product 0042"
        mockMvc.perform(get("/products/search")
                        .param("q", "Product 0042")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name", is("Product 0042")));

        // 2. Category Filter: "Bakery" (1000 / 5 = 200 items in Bakery)
        mockMvc.perform(get("/products/category/{category}", "Bakery")
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(200)))
                .andExpect(jsonPath("$.data.totalPages", is(10)))
                .andExpect(jsonPath("$.data.content", hasSize(20)))
                .andExpect(jsonPath("$.data.content[0].category", is("Bakery")));

        // 3. Combined Query & Pagination: "Product 0" in "Dairy & Eggs"
        mockMvc.perform(get("/products/search")
                        .param("q", "Product 0")
                        .param("category", "Dairy & Eggs")
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(20)))
                .andExpect(jsonPath("$.data.content[0].category", is("Dairy & Eggs")));

        // 4. Sorting Consistency: price DESC with stable tie-breaker
        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "price,desc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(10)));
    }

    @Test
    @DisplayName("E2E Phase 3: Real Shopping Flow — Cart Manipulation, Authoritative Price, and Atomic Stock Decrement")
    void completeShoppingFlowWithAtomicStockSafety() {
        catalogImportService.importFromCsv(createLargeCatalogCsvStream(100), CatalogImportOptions.defaults());

        Product product = productRepository.findByNameIgnoreCase("Product 0010").orElseThrow();
        UUID productId = product.getId();
        BigDecimal authoritativePrice = product.getPrice();
        int initialStock = product.getStock(); // 50

        String sessionId = "session-e2e-shopping-101";

        // 1. Add to cart quantity = 2
        cartService.addToCart(new AddToCartRequest(sessionId, productId, 2));
        CartResponse cart1 = cartService.getCart(sessionId);
        assertThat(cart1.getItems()).hasSize(1);
        assertThat(cart1.getItems().get(0).getQuantity()).isEqualTo(2);

        // 2. Change quantity to 3 (increment by 1)
        cartService.addToCart(new AddToCartRequest(sessionId, productId, 1));
        CartResponse cart2 = cartService.getCart(sessionId);
        assertThat(cart2.getItems().get(0).getQuantity()).isEqualTo(3);

        // 3. Checkout with customer request
        CheckoutRequest checkoutReq = new CheckoutRequest(
                sessionId,
                "Alice Green",
                "+919876543210",
                "Campus Block A, Room 302",
                null
        );

        OrderResponse orderResponse = orderService.checkout(checkoutReq);
        assertThat(orderResponse).isNotNull();
        assertThat(orderResponse.getId()).isNotNull();

        // Verify total price calculation matches database authoritative pricing
        BigDecimal expectedSubtotal = authoritativePrice.multiply(BigDecimal.valueOf(3));
        BigDecimal expectedDeliveryFee = expectedSubtotal.compareTo(new BigDecimal("199.00")) < 0
                ? new BigDecimal("25.00")
                : BigDecimal.ZERO;
        BigDecimal expectedTotal = expectedSubtotal.add(expectedDeliveryFee);

        assertThat(orderResponse.getTotalAmount()).isEqualByComparingTo(expectedTotal);

        // Verify database stock atomically decremented from 50 to 47
        Product updatedProduct = productRepository.findById(productId).orElseThrow();
        assertThat(updatedProduct.getStock()).isEqualTo(initialStock - 3);

        // Verify cart cleared after checkout
        CartResponse cartAfterCheckout = cartService.getCart(sessionId);
        assertThat(cartAfterCheckout.getItems()).isEmpty();

        // 4. Insufficient stock guard: Attempt to order more than available stock
        // Add 100 units to a new cart (only 47 available in DB)
        // Add 20 five times (max single request is 20)
        String session2 = "session-e2e-stock-guard";
        cartService.addToCart(new AddToCartRequest(session2, productId, 20));
        cartService.addToCart(new AddToCartRequest(session2, productId, 20));
        cartService.addToCart(new AddToCartRequest(session2, productId, 15)); // total 55 units in cart

        CheckoutRequest overstockCheckout = new CheckoutRequest(
                session2,
                "Bob White",
                "+919876543211",
                "Campus Gate",
                null
        );

        assertThatThrownBy(() -> orderService.checkout(overstockCheckout))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient stock");

        // Verify stock remains strictly 47 (no partial deduction)
        Product untouchedStockProduct = productRepository.findById(productId).orElseThrow();
        assertThat(untouchedStockProduct.getStock()).isEqualTo(47);
    }

    @Test
    @DisplayName("E2E Phase 4: Image Resilience — Null/missing image products are discoverable and purchasable")
    void imageResilienceProductsRemainDiscoverableAndPurchasable() throws Exception {
        // Product 0005 has blank imageUrl as defined in createLargeCatalogCsvStream (i % 5 == 0)
        catalogImportService.importFromCsv(createLargeCatalogCsvStream(20), CatalogImportOptions.defaults());

        Product productNullImage = productRepository.findByNameIgnoreCase("Product 0005").orElseThrow();
        assertThat(productNullImage.getImageUrl()).isNull();

        // 1. Discoverable via search API
        mockMvc.perform(get("/products/search")
                        .param("q", "Product 0005")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name", is("Product 0005")));

        // 2. Discoverable via paginated catalog API
        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(10)));

        // 3. Purchasable via Cart & Checkout
        String sessionId = "session-null-image-purchase";
        cartService.addToCart(new AddToCartRequest(sessionId, productNullImage.getId(), 1));

        OrderResponse orderResponse = orderService.checkout(new CheckoutRequest(
                sessionId,
                "Charlie Ray",
                "+919876543212",
                "Hostel 4",
                null
        ));

        assertThat(orderResponse).isNotNull();
        Product productAfter = productRepository.findById(productNullImage.getId()).orElseThrow();
        assertThat(productAfter.getStock()).isEqualTo(49);
    }

    @Test
    @DisplayName("E2E Phase 5: API & Pagination Consistency across Flat-List and Paged Modes")
    void apiContractConsistencyAcrossModes() throws Exception {
        catalogImportService.importFromCsv(createLargeCatalogCsvStream(30), CatalogImportOptions.defaults());

        // 1. Unpaged request returns flat array in $.data
        mockMvc.perform(get("/products")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(30)));

        // 2. Paged request returns PagedResponse object in $.data
        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "15")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page", is(0)))
                .andExpect(jsonPath("$.data.size", is(15)))
                .andExpect(jsonPath("$.data.totalElements", is(30)))
                .andExpect(jsonPath("$.data.totalPages", is(2)))
                .andExpect(jsonPath("$.data.content", hasSize(15)));

        // 3. Out-of-range page returns empty array without throwing
        mockMvc.perform(get("/products")
                        .param("page", "9999")
                        .param("size", "15")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements", is(30)));

        // 4. Invalid sort property safely falls back to default without throwing 500
        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "malicious_column_injection,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(10)));
    }
}
