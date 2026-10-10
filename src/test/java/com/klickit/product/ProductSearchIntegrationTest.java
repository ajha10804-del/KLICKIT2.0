package com.klickit.product;

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
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ProductSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();

        // 1. Whole Milk (Dairy & Eggs, active)
        productRepository.save(Product.builder()
                .name("Whole Milk")
                .description("Fresh farm whole milk 1L carton")
                .category("Dairy & Eggs")
                .price(new BigDecimal("60.00"))
                .active(true)
                .stock(50)
                .build());

        // 2. Green Apple (Fruits & Veg, active)
        productRepository.save(Product.builder()
                .name("Green Apple")
                .description("Crisp and tart Granny Smith apple")
                .category("Fruits & Veg")
                .price(new BigDecimal("120.00"))
                .active(true)
                .stock(30)
                .build());

        // 3. Apple Cider (Snacks & Drinks, active)
        productRepository.save(Product.builder()
                .name("Apple Cider")
                .description("Sparkling spiced apple beverage")
                .category("Snacks & Drinks")
                .price(new BigDecimal("95.00"))
                .active(true)
                .stock(20)
                .build());

        // 4. Inactive Apple (Fruits & Veg, inactive)
        productRepository.save(Product.builder()
                .name("Red Delicious Apple")
                .description("Sweet red apple")
                .category("Fruits & Veg")
                .price(new BigDecimal("100.00"))
                .active(false)
                .stock(10)
                .build());

        // 5. Sourdough Bread (Bakery, active)
        productRepository.save(Product.builder()
                .name("Sourdough Bread")
                .description("Artisan baked bread loaf")
                .category("Bakery")
                .price(new BigDecimal("80.00"))
                .active(true)
                .stock(15)
                .build());
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("Requirement 1: Partial match — query 'mil' matches 'Whole Milk'")
    void partialMatchFindsProduct() throws Exception {
        mockMvc.perform(get("/products/search")
                        .param("q", "mil")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name", is("Whole Milk")));
    }

    @Test
    @DisplayName("Requirement 2: Case-insensitive match — query 'APPLE' matches 'Green Apple' and 'Apple Cider'")
    void caseInsensitiveMatchFindsProducts() throws Exception {
        mockMvc.perform(get("/products/search")
                        .param("q", "APPLE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @DisplayName("Requirement 3: Inactive products are strictly excluded from search results")
    void inactiveProductsAreExcluded() throws Exception {
        // Red Delicious Apple matches 'apple' and 'red', but active=false
        mockMvc.perform(get("/products/search")
                        .param("q", "Delicious")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("Requirement 4: Category filter only — specifying category returns only active products in that category")
    void categoryFilterOnlyReturnsActiveCategoryProducts() throws Exception {
        mockMvc.perform(get("/products/search")
                        .param("category", "Bakery")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name", is("Sourdough Bread")));
    }

    @Test
    @DisplayName("Requirement 5: Combined search and category filter — matches both conditions")
    void combinedSearchAndCategoryFiltering() throws Exception {
        // 'apple' matches Green Apple (Fruits & Veg) and Apple Cider (Snacks & Drinks)
        // With category="Fruits & Veg", only Green Apple must be returned
        mockMvc.perform(get("/products/search")
                        .param("q", "apple")
                        .param("category", "Fruits & Veg")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name", is("Green Apple")));
    }

    @Test
    @DisplayName("Requirement 6: Empty / Whitespace query returns all active products")
    void emptyOrWhitespaceQueryReturnsAllActive() throws Exception {
        mockMvc.perform(get("/products/search")
                        .param("q", "   ")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                // Total active products is 4 (Whole Milk, Green Apple, Apple Cider, Sourdough Bread)
                .andExpect(jsonPath("$.data", hasSize(4)));
    }

    @Test
    @DisplayName("Requirement 6b: Empty / Whitespace query with category returns all active in category")
    void emptyQueryWithCategoryReturnsCategoryActive() throws Exception {
        mockMvc.perform(get("/products/search")
                        .param("q", "  ")
                        .param("category", "  Dairy & Eggs  ")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name", is("Whole Milk")));
    }

    @Test
    @DisplayName("Requirement 7: No results found returns empty list with HTTP 200")
    void noResultsReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/products/search")
                        .param("q", "nonexistent-item-xyz")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("Description search — query matching description returns product")
    void descriptionMatchFindsProduct() throws Exception {
        mockMvc.perform(get("/products/search")
                        .param("q", "Granny Smith")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name", is("Green Apple")));
    }

    @Test
    @DisplayName("Existing category endpoint backward compatibility")
    void existingCategoryEndpointRemainsCompatible() throws Exception {
        mockMvc.perform(get("/products/category/{category}", "Dairy & Eggs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name", is("Whole Milk")));
    }
}
