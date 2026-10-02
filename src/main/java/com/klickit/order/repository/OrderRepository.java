package com.klickit.order.repository;

import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    List<Order> findAllByOrderByCreatedAtDesc();

    List<Order> findByDeliveryPartnerIdAndStatusInOrderByDeadlineAsc(
            UUID deliveryPartnerId, List<OrderStatus> statuses);

    List<Order> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail);

    @Modifying
    @Query("update Order o set o.notificationSent = :sent where o.id = :id")
    int updateNotificationStatus(@Param("id") UUID id, @Param("sent") boolean sent);
}
