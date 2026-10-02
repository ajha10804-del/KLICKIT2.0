package com.klickit.notification.client;

/**
 * Abstraction for sending transactional emails via HTTPS API.
 * Decouples order notification from the underlying email transport provider (e.g., Resend, Brevo, SendGrid).
 */
public interface TransactionalEmailClient {

    /**
     * Sends an email via transactional email HTTPS API.
     *
     * @param to recipient email address
     * @param subject email subject line
     * @param htmlBody HTML email body
     * @return true if the email was successfully accepted by the provider, false otherwise
     */
    boolean sendEmail(String to, String subject, String htmlBody);
}
