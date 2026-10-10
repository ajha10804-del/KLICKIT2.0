package com.klickit.cart.repository;

import com.klickit.cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findBySessionId(String sessionId);

    @Modifying
    @Query("update Cart c set c.customerEmail = :email where c.sessionId = :sessionId and (c.customerEmail is null or c.customerEmail = '')")
    int claimCartForCustomer(@Param("sessionId") String sessionId, @Param("email") String email);

    @Modifying
    @Query("delete from Cart c where c.updatedAt < :cutoff")
    int deleteByUpdatedAtBefore(@Param("cutoff") Instant cutoff);
}
