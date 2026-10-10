package com.klickit.product.controller;

import com.klickit.common.dto.ApiResponse;
import com.klickit.common.dto.PagedResponse;
import com.klickit.product.dto.CreateProductRequest;
import com.klickit.product.dto.ProductResponse;
import com.klickit.product.dto.UpdateProductRequest;
import com.klickit.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog browsing and catalog management APIs")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Get all active products", description = "Public endpoint to retrieve active products with optional pagination and sorting")
    @GetMapping
    public ResponseEntity<ApiResponse<?>> getAllProducts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        if (page != null || size != null || sort != null) {
            int pageNum = page != null ? page : 0;
            int pageSize = size != null ? size : 20;
            PagedResponse<ProductResponse> paged = productService.getProductsPaged(pageNum, pageSize, sort);
            return ResponseEntity.ok(ApiResponse.success(paged));
        }
        List<ProductResponse> products = productService.getAllProducts();
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @Operation(summary = "Get product by ID", description = "Public endpoint to retrieve product details by its unique identifier")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable UUID id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @Operation(summary = "Get products by category", description = "Public endpoint to filter active products by category name with optional pagination")
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<?>> getProductsByCategory(
            @PathVariable String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        if (page != null || size != null || sort != null) {
            int pageNum = page != null ? page : 0;
            int pageSize = size != null ? size : 20;
            PagedResponse<ProductResponse> paged = productService.getProductsByCategoryPaged(category, pageNum, pageSize, sort);
            return ResponseEntity.ok(ApiResponse.success(paged));
        }
        List<ProductResponse> products = productService.getProductsByCategory(category);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @Operation(summary = "Search active products", description = "Public endpoint to search active products with optional query text, category filter, and pagination")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<?>> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        if (page != null || size != null || sort != null) {
            int pageNum = page != null ? page : 0;
            int pageSize = size != null ? size : 20;
            PagedResponse<ProductResponse> paged = productService.searchProductsPaged(q, category, pageNum, pageSize, sort);
            return ResponseEntity.ok(ApiResponse.success(paged));
        }
        List<ProductResponse> products = productService.searchProducts(q, category);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @Operation(summary = "Create product", description = "Admin-only endpoint to create a new product in the catalog")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", product));
    }

    @Operation(summary = "Update product", description = "Admin-only endpoint to update an existing product")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductResponse product = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", product));
    }

    @Operation(summary = "Delete product", description = "Admin-only endpoint to deactivate/delete a product from the catalog")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }
}
