package com.klickit.notification.client;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HttpsTransactionalEmailClient implements TransactionalEmailClient {

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public HttpsTransactionalEmailClient(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    @PostConstruct
    public void validateConfigurationOnStartup() {
        log.info("Gmail SMTP email client initialized for sender: [{}]", fromEmail);
    }

    @Override
    public boolean sendEmail(String to, String subject, String htmlBody) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();

            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);

            message.setText(htmlToPlainText(htmlBody));

            mailSender.send(message);

            log.info("Email successfully sent to recipient: [{}]", to);
            return true;

        } catch (Exception ex) {
            log.error(
                    "Email delivery failed for recipient: [{}]. Error: [{}]",
                    to,
                    ex.getMessage()
            );
            return false;
        }
    }

    private String htmlToPlainText(String html) {
        if (html == null) {
            return "";
        }

        return html
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("</p>", "\n")
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .trim();
    }
}
