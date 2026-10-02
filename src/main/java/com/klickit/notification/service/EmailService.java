package com.klickit.notification.service;

import com.klickit.notification.client.TransactionalEmailClient;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Service responsible for constructing safe HTML order notification emails
 * and dispatching them via the TransactionalEmailClient.
 * Executes synchronously within the asynchronous OrderNotificationListener.
 */
@Service
@Slf4j
public class EmailService {

    private final TransactionalEmailClient emailClient;
    private final String adminEmail;
    private final ZoneId displayZoneId;
    private final DateTimeFormatter dateFormatter;

    public EmailService(
            TransactionalEmailClient emailClient,
            @Value("${klickit.admin.email}") String adminEmail,
            @Value("${klickit.mail.timezone:Asia/Kolkata}") String mailTimezone) {
        this.emailClient = emailClient;
        this.adminEmail = adminEmail;
        this.displayZoneId = ZoneId.of(mailTimezone != null && !mailTimezone.isBlank() ? mailTimezone.trim() : "Asia/Kolkata");
        this.dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.UK).withZone(displayZoneId);
    }

    public boolean sendOrderConfirmationToAdmin(Order order) {
        return sendOrderConfirmationToAdmin(order, order != null ? order.getItems() : null);
    }

    public boolean sendOrderConfirmationToAdmin(Order order, List<OrderItem> items) {
        if (order == null) {
            log.warn("Cannot send order confirmation: Order is null");
            return false;
        }

        if (adminEmail == null || adminEmail.isBlank()) {
            log.error("Cannot send order confirmation: ADMIN_EMAIL is not configured");
            return false;
        }

        try {
            String subject = "New KLICKIT Order Received - Order #" + order.getId();
            String htmlBody = buildOrderEmailBody(order, items);

            boolean success = emailClient.sendEmail(adminEmail, subject, htmlBody);
            if (success) {
                log.info("Order confirmation email successfully dispatched to admin for order: {}", order.getId());
            } else {
                log.warn("Failed to dispatch order confirmation email to admin for order: {}", order.getId());
            }
            return success;
        } catch (Exception e) {
            log.error("Exception during order confirmation email dispatch for order: {}. Reason: {}",
                    order.getId(), e.getMessage(), e);
            return false;
        }
    }

    public String buildOrderEmailBody(Order order) {
        return buildOrderEmailBody(order, order != null ? order.getItems() : null);
    }

    public String buildOrderEmailBody(Order order, List<OrderItem> items) {
        if (order == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("<html><body style='font-family: Arial, sans-serif; color: #333;'>");
        sb.append("<h2 style='color: #2c3e50;'>New KLICKIT Order Received</h2>");
        sb.append("<hr style='border: 1px solid #eee;'>");

        // Order details - all customer-controlled fields are HTML escaped to prevent stored XSS injection
        sb.append("<table style='width: 100%; border-collapse: collapse; margin: 16px 0;'>");
        appendRow(sb, "Order ID", order.getId() != null ? order.getId().toString() : "N/A");
        appendRow(sb, "Customer Name", HtmlUtils.htmlEscape(order.getCustomerName() != null ? order.getCustomerName() : "N/A"));
        appendRow(sb, "Customer Phone", HtmlUtils.htmlEscape(order.getCustomerPhone() != null ? order.getCustomerPhone() : "N/A"));
        appendRow(sb, "Delivery Address", HtmlUtils.htmlEscape(order.getCustomerAddress() != null ? order.getCustomerAddress() : "N/A"));
        appendRow(sb, "Deadline", order.getDeadline() != null
                ? dateFormatter.format(order.getDeadline())
                : "Standard Delivery (ASAP)");
        sb.append("</table>");

        // Order items / Products
        sb.append("<h3 style='color: #2c3e50;'>Products</h3>");
        sb.append("<table style='width: 100%; border-collapse: collapse; border: 1px solid #ddd;'>");
        sb.append("<tr style='background-color: #f8f9fa;'>");
        sb.append("<th style='padding: 10px; text-align: left; border: 1px solid #ddd;'>Product</th>");
        sb.append("<th style='padding: 10px; text-align: center; border: 1px solid #ddd;'>Qty</th>");
        sb.append("<th style='padding: 10px; text-align: right; border: 1px solid #ddd;'>Price</th>");
        sb.append("<th style='padding: 10px; text-align: right; border: 1px solid #ddd;'>Total</th>");
        sb.append("</tr>");

        List<OrderItem> itemList = items != null ? items : order.getItems();
        if (itemList != null && !itemList.isEmpty()) {
            for (OrderItem item : itemList) {
                BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                int qty = item.getQuantity();
                BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(qty));
                sb.append("<tr>");
                sb.append("<td style='padding: 10px; border: 1px solid #ddd;'>")
                        .append(HtmlUtils.htmlEscape(item.getProductName() != null ? item.getProductName() : "Item"))
                        .append("</td>");
                sb.append("<td style='padding: 10px; text-align: center; border: 1px solid #ddd;'>")
                        .append(qty)
                        .append("</td>");
                sb.append("<td style='padding: 10px; text-align: right; border: 1px solid #ddd;'>₹")
                        .append(price)
                        .append("</td>");
                sb.append("<td style='padding: 10px; text-align: right; border: 1px solid #ddd;'>₹")
                        .append(lineTotal)
                        .append("</td>");
                sb.append("</tr>");
            }
        }

        sb.append("</table>");

        // Total
        BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        sb.append("<h3 style='color: #2c3e50; text-align: right; margin-top: 16px;'>");
        sb.append("Total: ₹").append(total);
        sb.append("</h3>");

        sb.append("<hr style='border: 1px solid #eee;'>");
        sb.append("<p style='color: #999; font-size: 12px;'>This is an automated notification from KLICKIT.</p>");
        sb.append("</body></html>");

        return sb.toString();
    }

    private void appendRow(StringBuilder sb, String label, String value) {
        sb.append("<tr>");
        sb.append("<td style='padding: 8px; font-weight: bold; width: 150px;'>").append(label).append("</td>");
        sb.append("<td style='padding: 8px;'>").append(value).append("</td>");
        sb.append("</tr>");
    }
}
