package com.klickit.notification.listener;

import com.klickit.notification.service.EmailService;
import com.klickit.order.event.OrderCreatedEvent;
import com.klickit.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Event listener that processes order notifications decoupled from order persistence.
 * Executes after transaction commit so that email delivery attempts or failures
 * never corrupt or roll back valid orders.
 * Updates order notificationSent status via atomic database update in a new transaction.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderNotificationListener {

    private final EmailService emailService;
    private final OrderService orderService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleOrderCreated(OrderCreatedEvent event) {
        if (event == null || event.getOrder() == null) {
            log.warn("Received empty or null OrderCreatedEvent, skipping notification");
            return;
        }

        UUID orderId = event.getOrder().getId();
        log.info("Processing order notification for order ID: {}", orderId);
        boolean sent = false;
        try {
            sent = emailService.sendOrderConfirmationToAdmin(event.getOrder(), event.getItems());
        } catch (Exception e) {
            log.error("Failed to process order email notification for order ID [{}]: {}",
                    orderId, e.getMessage(), e);
        } finally {
            if (orderId != null) {
                try {
                    orderService.updateNotificationStatus(orderId, sent);
                    log.info("Successfully updated order [{}] notificationSent status to: {}", orderId, sent);
                } catch (Exception e) {
                    log.error("Failed to update notification status for order ID [{}]: {}", orderId, e.getMessage(), e);
                }
            }
        }
    }
}
