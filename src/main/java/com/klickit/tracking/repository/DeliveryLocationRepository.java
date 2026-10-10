package com.klickit.tracking.repository;

import com.klickit.tracking.entity.DeliveryLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryLocationRepository extends JpaRepository<DeliveryLocation, UUID> {

    Optional<DeliveryLocation> findByOrderId(UUID orderId);

    void deleteByOrderId(UUID orderId);
}
