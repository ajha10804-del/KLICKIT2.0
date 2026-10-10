package com.klickit.notification.service;

import com.klickit.notification.client.TransactionalEmailClient;
import com.klickit.order.entity.Order;
import com.klickit.order.entity.OrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.annotation.PostConstruct;
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

    @PostConstruct
    public void validateConfigurationOnStartup() {
        if (adminEmail == null || adminEmail.isBlank()) {
            throw new IllegalStateException("ADMIN_EMAIL must be configured");
        }
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
        appendRow(sb, "Order Time", order.getCreatedAt() != null
                ? dateFormatter.format(order.getCreatedAt())
                : (order.getDeadline() != null ? dateFormatter.format(order.getDeadline().minus(15, java.time.temporal.ChronoUnit.MINUTES)) : "Standard Delivery (ASAP)"));
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
                        .append(price.setScale(2, java.math.RoundingMode.HALF_UP))
                        .append("</td>");
                sb.append("<td style='padding: 10px; text-align: right; border: 1px solid #ddd;'>₹")
                        .append(lineTotal.setScale(2, java.math.RoundingMode.HALF_UP))
                        .append("</td>");
                sb.append("</tr>");
            }
        }

        sb.append("</table>");

        // Authoritative COD Financial Breakdown: Subtotal, Delivery Fee, Total Amount
        BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal fee = order.getDeliveryFee() != null ? order.getDeliveryFee() : BigDecimal.ZERO;
        BigDecimal subtotal = total.subtract(fee);
        if (subtotal.compareTo(BigDecimal.ZERO) < 0) {
            subtotal = BigDecimal.ZERO;
        }

        sb.append("<table style='width: 100%; border-collapse: collapse; margin-top: 16px;'>");
        sb.append("<tr>");
        sb.append("<td style='padding: 6px; text-align: right; font-weight: bold;'>Subtotal:</td>");
        sb.append("<td style='padding: 6px; text-align: right; width: 140px;'>₹").append(subtotal.setScale(2, java.math.RoundingMode.HALF_UP)).append("</td>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("<td style='padding: 6px; text-align: right; font-weight: bold;'>Delivery Fee:</td>");
        sb.append("<td style='padding: 6px; text-align: right; width: 140px;'>")
          .append(fee.compareTo(BigDecimal.ZERO) == 0 ? "FREE" : "₹" + fee.setScale(2, java.math.RoundingMode.HALF_UP))
          .append("</td>");
        sb.append("</tr>");
        sb.append("<tr style='border-top: 2px solid #2c3e50;'>");
        sb.append("<td style='padding: 8px; text-align: right; font-weight: bold; font-size: 16px;'>Total (COD Amount):</td>");
        sb.append("<td style='padding: 8px; text-align: right; font-weight: bold; font-size: 16px; color: #16a34a; width: 140px;'>₹").append(total.setScale(2, java.math.RoundingMode.HALF_UP)).append("</td>");
        sb.append("</tr>");
        sb.append("</table>");

        sb.append("<hr style='border: 1px solid #eee; margin-top: 20px;'>");
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

    public boolean sendLoginCode(String to, String code, long validMinutes) {
        if (to == null || to.isBlank() || code == null || code.isBlank()) {
            log.warn("Cannot send login code: email or code is empty");
            return false;
        }

        try {
            String subject = "Your KLICKIT Sign-in Code";
            String escapedCode = HtmlUtils.htmlEscape(code.trim());
            String htmlBody = "<html><body style='font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;'>"
                    + "<h2 style='color: #2c3e50; border-bottom: 2px solid #e2e8f0; padding-bottom: 8px;'>Your KLICKIT Sign-in Code</h2>"
                    + "<p>Use the following 6-digit verification code to sign in to your KLICKIT account:</p>"
                    + "<div style='font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #2563eb; background: #eff6ff; padding: 16px 24px; text-align: center; border-radius: 8px; margin: 24px 0;'>"
                    + escapedCode
                    + "</div>"
                    + "<p style='color: #64748b; font-size: 14px;'>This code will expire in " + validMinutes + " minutes. If you did not request this code, you can safely ignore this email.</p>"
                    + "<hr style='border: 1px solid #eee; margin-top: 24px;'>"
                    + "<p style='color: #999; font-size: 12px;'>This is an automated notification from KLICKIT.</p>"
                    + "</body></html>";

            boolean success = emailClient.sendEmail(to.trim(), subject, htmlBody);
            if (success) {
                log.info("Successfully dispatched login code email");
            } else {
                log.warn("Failed to dispatch login code email");
            }
            return success;
        } catch (Exception e) {
            log.error("Exception while sending login code email: {}", e.getMessage());
            return false;
        }
    }
}
