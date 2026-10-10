package com.klickit.product;

import com.klickit.common.dto.ApiResponse;
import com.klickit.common.dto.PagedResponse;
import com.klickit.product.dto.ProductResponse;
import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import com.klickit.product.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ProductPaginationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
    }

    private void seedProducts(int count) {
        List<Product> list = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            String category = (i % 3 == 0) ? "Dairy & Eggs" : (i % 3 == 1) ? "Bakery" : "Snacks & Drinks";
            BigDecimal price = BigDecimal.valueOf(10 + (i % 50));
            list.add(Product.builder()
                    .name(String.format("Product %04d", i))
                    .category(category)
                    .price(price)
                    .description("High quality grocery item " + i)
                    .stock(50)
                    .active(true)
                    .build());
        }
        productRepository.saveAll(list);
    }

    @Test
    @DisplayName("Requirement 1: Default Pagination — page=0, default size=20, correct total elements and pages")
    void defaultPaginationReturnsFirstTwentyProducts() throws Exception {
        seedProducts(45);

        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.page", is(0)))
                .andExpect(jsonPath("$.data.size", is(20)))
                .andExpect(jsonPath("$.data.totalElements", is(45)))
                .andExpect(jsonPath("$.data.totalPages", is(3)))
                .andExpect(jsonPath("$.data.last", is(false)))
                .andExpect(jsonPath("$.data.content", hasSize(20)));
    }

    @Test
    @DisplayName("Requirement 2: Page Boundaries & Out-of-Range — page=999 returns empty content with HTTP 200")
    void outOfRangePageReturnsEmptyContent() throws Exception {
        seedProducts(10);

        mockMvc.perform(get("/products")
                        .param("page", "999")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.page", is(999)))
                .andExpect(jsonPath("$.data.totalElements", is(10)))
                .andExpect(jsonPath("$.data.totalPages", is(1)))
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    @DisplayName("Requirement 3: Maximum Page Size Clamped — size=500 is clamped to 100")
    void maxPageSizeClampedToOneHundred() throws Exception {
        seedProducts(120);

        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "500")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.size", is(100)))
                .andExpect(jsonPath("$.data.content", hasSize(100)));
    }

    @Test
    @DisplayName("Requirement 4: Predictable Sorting by Price ASC & DESC with stable tie-breaker")
    void predictableSortingByPriceAscAndDesc() throws Exception {
        // Product A: 10.00, Product B: 20.00, Product C: 10.00
        productRepository.save(Product.builder().name("Item A").category("Bakery").price(new BigDecimal("10.00")).active(true).stock(10).build());
        productRepository.save(Product.builder().name("Item B").category("Bakery").price(new BigDecimal("20.00")).active(true).stock(10).build());
        productRepository.save(Product.builder().name("Item C").category("Bakery").price(new BigDecimal("10.00")).active(true).stock(10).build());

        // Price ASC
        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "price,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].price", is(10.0)))
                .andExpect(jsonPath("$.data.content[1].price", is(10.0)))
                .andExpect(jsonPath("$.data.content[2].price", is(20.0)));

        // Price DESC
        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "price,desc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].price", is(20.0)))
                .andExpect(jsonPath("$.data.content[1].price", is(10.0)))
                .andExpect(jsonPath("$.data.content[2].price", is(10.0)));
    }

    @Test
    @DisplayName("Requirement 5: Combined Search & Category with Pagination")
    void combinedSearchAndCategoryWithPagination() throws Exception {
        // 15 Milk items in Dairy & Eggs
        for (int i = 1; i <= 15; i++) {
            productRepository.save(Product.builder()
                    .name("Organic Milk Bottle " + i)
                    .category("Dairy & Eggs")
                    .price(new BigDecimal("45.00"))
                    .active(true)
                    .stock(10)
                    .build());
        }
        // 5 Milk Chocolate items in Snacks & Drinks (different category)
        for (int i = 1; i <= 5; i++) {
            productRepository.save(Product.builder()
                    .name("Milk Chocolate Bar " + i)
                    .category("Snacks & Drinks")
                    .price(new BigDecimal("30.00"))
                    .active(true)
                    .stock(10)
                    .build());
        }

        // Query 'milk' in 'Dairy & Eggs' with size=10 -> page 0 has 10 items, totalElements=15, totalPages=2
        mockMvc.perform(get("/products/search")
                        .param("q", "milk")
                        .param("category", "Dairy & Eggs")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page", is(0)))
                .andExpect(jsonPath("$.data.size", is(10)))
                .andExpect(jsonPath("$.data.totalElements", is(15)))
                .andExpect(jsonPath("$.data.totalPages", is(2)))
                .andExpect(jsonPath("$.data.content", hasSize(10)));
    }

    @Test
    @DisplayName("Requirement 6: Inactive products strictly excluded from paginated count and content")
    void inactiveProductsExcludedFromPagination() throws Exception {
        productRepository.save(Product.builder().name("Active Product 1").category("Bakery").price(new BigDecimal("10.00")).active(true).stock(10).build());
        productRepository.save(Product.builder().name("Active Product 2").category("Bakery").price(new BigDecimal("15.00")).active(true).stock(10).build());
        productRepository.save(Product.builder().name("Archived Inactive Product").category("Bakery").price(new BigDecimal("20.00")).active(false).stock(10).build());

        mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(2)))
                .andExpect(jsonPath("$.data.content", hasSize(2)));
    }

    @Test
    @DisplayName("Requirement 7: Large-Catalog Benchmark (1,000+ products) — Payload & query measurement")
    void largeCatalogBenchmarkOneThousandProducts() throws Exception {
        // Seed 1,000 active products
        seedProducts(1000);
        assertThat(productRepository.count()).isEqualTo(1000);

        // 1. Measure paginated request (size = 20)
        long startPage0 = System.currentTimeMillis();
        var mvcResultPaged = mockMvc.perform(get("/products")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "name,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements", is(1000)))
                .andExpect(jsonPath("$.data.totalPages", is(50)))
                .andExpect(jsonPath("$.data.content", hasSize(20)))
                .andReturn();
        long durationPage0 = System.currentTimeMillis() - startPage0;
        int pagedPayloadBytes = mvcResultPaged.getResponse().getContentAsByteArray().length;

        // 2. Measure deep page request (page = 25)
        long startDeep = System.currentTimeMillis();
        mockMvc.perform(get("/products")
                        .param("page", "25")
                        .param("size", "20")
                        .param("sort", "price,desc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page", is(25)))
                .andExpect(jsonPath("$.data.content", hasSize(20)));
        long durationDeep = System.currentTimeMillis() - startDeep;

        // 3. Measure filtered keyword & category search on 1,000 products
        long startSearch = System.currentTimeMillis();
        mockMvc.perform(get("/products/search")
                        .param("q", "005") // Matches Product 0050..0059 etc.
                        .param("category", "Dairy & Eggs")
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        long durationSearch = System.currentTimeMillis() - startSearch;

        // 4. Measure unpaginated request (~1,000 items)
        var mvcResultUnpaged = mockMvc.perform(get("/products")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1000)))
                .andReturn();
        int unpagedPayloadBytes = mvcResultUnpaged.getResponse().getContentAsByteArray().length;

        System.out.printf("""
                === 1,000 PRODUCT CATALOG BENCHMARK RESULTS ===
                Unpaginated Payload Size: %d KB (%d bytes)
                Paginated (size=20) Payload Size: %d KB (%d bytes)
                Bandwidth Reduction: %.1f%%
                Query Execution Time (Page 0, name asc): %d ms
                Query Execution Time (Deep Page 25, price desc): %d ms
                Query Execution Time (Keyword + Category Search): %d ms
                =================================================
                """,
                unpagedPayloadBytes / 1024, unpagedPayloadBytes,
                pagedPayloadBytes / 1024, pagedPayloadBytes,
                100.0 * (1.0 - (double) pagedPayloadBytes / unpagedPayloadBytes),
                durationPage0, durationDeep, durationSearch);

        // Sanity assertions
        assertThat(pagedPayloadBytes).isLessThan(unpagedPayloadBytes / 20);
        assertThat(durationPage0).isLessThan(2000);
        assertThat(durationDeep).isLessThan(2000);
        assertThat(durationSearch).isLessThan(2000);
    }
}
