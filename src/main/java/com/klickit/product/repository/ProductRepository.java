package com.klickit.product.repository;

import com.klickit.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByCategoryIgnoreCase(String category);

    List<Product> findByActiveTrue();

    List<Product> findByCategoryIgnoreCaseAndActiveTrue(String category);

    boolean existsByNameIgnoreCase(String name);
}
