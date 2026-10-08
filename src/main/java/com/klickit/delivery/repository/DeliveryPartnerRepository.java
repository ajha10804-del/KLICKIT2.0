package com.klickit.delivery.repository;

import com.klickit.delivery.entity.DeliveryPartner;
import com.klickit.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, UUID> {

    Optional<DeliveryPartner> findByUser(User user);

    Optional<DeliveryPartner> findByUserEmail(String email);

    @Query("SELECT dp FROM DeliveryPartner dp WHERE dp.user.id = :userId")
    Optional<DeliveryPartner> findByUserId(@Param("userId") UUID userId);

    Optional<DeliveryPartner> findByPhone(String phone);
}
