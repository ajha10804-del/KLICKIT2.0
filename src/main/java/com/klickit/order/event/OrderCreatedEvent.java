package com.klickit.order.event;

import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderItem;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Domain event published when an order has been successfully created and persisted.
 * Decouples order transaction processing from notification and email side-effects.
 */
@Getter
public class OrderCreatedEvent {

    private final Order order;
    private final List<OrderItem> items;

    public OrderCreatedEvent(Order order) {
        this.order = order;
        this.items = (order != null && order.getItems() != null)
                ? new ArrayList<>(order.getItems())
                : Collections.emptyList();
    }

    public OrderCreatedEvent(Order order, List<OrderItem> items) {
        this.order = order;
        this.items = items != null ? new ArrayList<>(items) : Collections.emptyList();
    }
}
