package com.klickit.order.repository;

import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    org.springframework.data.domain.Page<Order> findAllBy(org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Order> findByStatus(OrderStatus status, org.springframework.data.domain.Pageable pageable);

    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    List<Order> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"items"})
    List<Order> findByDeliveryPartnerIdAndStatusInOrderByDeadlineAsc(
            UUID deliveryPartnerId, List<OrderStatus> statuses);

    List<Order> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail);

    @Modifying
    @Query("update Order o set o.notificationSent = :sent where o.id = :id")
    int updateNotificationStatus(@Param("id") UUID id, @Param("sent") boolean sent);
}
