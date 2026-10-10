package com.klickit.product.service;

import com.klickit.common.dto.PagedResponse;
import com.klickit.common.exception.ResourceNotFoundException;
import com.klickit.product.dto.CreateProductRequest;
import com.klickit.product.dto.ProductResponse;
import com.klickit.product.dto.UpdateProductRequest;
import com.klickit.product.entity.Product;
import com.klickit.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("name", "price", "createdAt");

    public List<ProductResponse> getAllProducts() {
        return productRepository.findByActiveTrue().stream()
                .map(ProductResponse::from)
                .toList();
    }

    public PagedResponse<ProductResponse> getProductsPaged(int page, int size, String sortParam) {
        Pageable pageable = buildSafePageable(page, size, sortParam);
        Page<ProductResponse> productPage = productRepository.findByActiveTrue(pageable)
                .map(ProductResponse::from);
        return PagedResponse.from(productPage);
    }

    public ProductResponse getProductById(UUID id) {
        Product product = findProductOrThrow(id);
        if (!product.isActive()) {
            throw new ResourceNotFoundException("Product", "id", id);
        }
        return ProductResponse.from(product);
    }

    public List<ProductResponse> getProductsByCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return getAllProducts();
        }
        return productRepository.findByCategoryIgnoreCaseAndActiveTrue(category.trim()).stream()
                .map(ProductResponse::from)
                .toList();
    }

    public PagedResponse<ProductResponse> getProductsByCategoryPaged(String category, int page, int size, String sortParam) {
        if (category == null || category.trim().isEmpty()) {
            return getProductsPaged(page, size, sortParam);
        }
        Pageable pageable = buildSafePageable(page, size, sortParam);
        Page<ProductResponse> productPage = productRepository.findByCategoryIgnoreCaseAndActiveTrue(category.trim(), pageable)
                .map(ProductResponse::from);
        return PagedResponse.from(productPage);
    }

    public List<ProductResponse> searchProducts(String query, String category) {
        String cleanQuery = query != null ? query.trim() : "";
        String cleanCategory = category != null ? category.trim() : "";

        boolean hasQuery = !cleanQuery.isEmpty();
        boolean hasCategory = !cleanCategory.isEmpty();

        List<Product> products;
        if (hasQuery && hasCategory) {
            products = productRepository.searchActiveByCategoryAndQuery(cleanCategory, cleanQuery);
        } else if (hasQuery) {
            products = productRepository.searchActiveByQuery(cleanQuery);
        } else if (hasCategory) {
            products = productRepository.findByCategoryIgnoreCaseAndActiveTrue(cleanCategory);
        } else {
            products = productRepository.findByActiveTrue();
        }

        return products.stream()
                .map(ProductResponse::from)
                .toList();
    }

    public PagedResponse<ProductResponse> searchProductsPaged(String query, String category, int page, int size, String sortParam) {
        String cleanQuery = query != null ? query.trim() : "";
        String cleanCategory = category != null ? category.trim() : "";

        boolean hasQuery = !cleanQuery.isEmpty();
        boolean hasCategory = !cleanCategory.isEmpty();

        Pageable pageable = buildSafePageable(page, size, sortParam);
        Page<Product> products;
        if (hasQuery && hasCategory) {
            products = productRepository.searchActiveByCategoryAndQuery(cleanCategory, cleanQuery, pageable);
        } else if (hasQuery) {
            products = productRepository.searchActiveByQuery(cleanQuery, pageable);
        } else if (hasCategory) {
            products = productRepository.findByCategoryIgnoreCaseAndActiveTrue(cleanCategory, pageable);
        } else {
            products = productRepository.findByActiveTrue(pageable);
        }

        Page<ProductResponse> responsePage = products.map(ProductResponse::from);
        return PagedResponse.from(responsePage);
    }

    public Pageable buildSafePageable(int page, int size, String sortParam) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        List<Sort.Order> orders = new ArrayList<>();
        if (sortParam != null && !sortParam.trim().isEmpty()) {
            String[] parts = sortParam.trim().split(",");
            String property = parts[0].trim();
            Sort.Direction direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;

            if (ALLOWED_SORT_PROPERTIES.contains(property)) {
                orders.add(new Sort.Order(direction, property));
            }
        }

        if (orders.isEmpty()) {
            orders.add(new Sort.Order(Sort.Direction.ASC, "name"));
        }

        // Stable tie-breaker: always append id ASC
        orders.add(new Sort.Order(Sort.Direction.ASC, "id"));

        return PageRequest.of(safePage, safeSize, Sort.by(orders));
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .imageUrl(request.getImageUrl())
                .category(request.getCategory())
                .stock(request.getStock() != null ? request.getStock() : 50)
                .build();

        Product saved = productRepository.save(product);
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        Product product = findProductOrThrow(id);

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl());
        }
        if (request.getCategory() != null) {
            product.setCategory(request.getCategory());
        }
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        if (request.getStock() != null) {
            product.setStock(request.getStock());
        }

        Product updated = productRepository.save(product);
        return ProductResponse.from(updated);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = findProductOrThrow(id);
        product.setActive(false);
        productRepository.save(product);
    }

    private Product findProductOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }
}
