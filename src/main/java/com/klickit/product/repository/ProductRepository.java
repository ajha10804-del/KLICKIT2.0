package com.klickit.product.repository;

import com.klickit.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByCategoryIgnoreCase(String category);

    List<Product> findByActiveTrue();

    Page<Product> findByActiveTrue(Pageable pageable);

    List<Product> findByCategoryIgnoreCaseAndActiveTrue(String category);

    Page<Product> findByCategoryIgnoreCaseAndActiveTrue(String category, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "(p.description IS NOT NULL AND LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))))")
    List<Product> searchActiveByQuery(@Param("q") String query);

    @Query(value = "SELECT p FROM Product p WHERE p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "(p.description IS NOT NULL AND LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))))",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "(p.description IS NOT NULL AND LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))))")
    Page<Product> searchActiveByQuery(@Param("q") String query, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
           "LOWER(p.category) = LOWER(:category) AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "(p.description IS NOT NULL AND LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))))")
    List<Product> searchActiveByCategoryAndQuery(@Param("category") String category, @Param("q") String query);

    @Query(value = "SELECT p FROM Product p WHERE p.active = true AND " +
           "LOWER(p.category) = LOWER(:category) AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "(p.description IS NOT NULL AND LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))))",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.active = true AND " +
           "LOWER(p.category) = LOWER(:category) AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "(p.description IS NOT NULL AND LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))))")
    Page<Product> searchActiveByCategoryAndQuery(@Param("category") String category, @Param("q") String query, Pageable pageable);

    boolean existsByNameIgnoreCase(String name);

    Optional<Product> findByNameIgnoreCase(String name);

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :productId AND p.stock >= :quantity AND p.active = true")
    int decrementStockIfAvailable(@Param("productId") UUID productId, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity WHERE p.id = :productId")
    int incrementStock(@Param("productId") UUID productId, @Param("quantity") int quantity);
}

