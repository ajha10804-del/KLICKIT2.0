package com.klickit.notification.client;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * HTTPS-based transactional email client (default provider: Resend API).
 * Configured with 5-second connect and read timeouts, 1 inline retry with ~1s backoff,
 * masked logging to ensure credentials are never leaked, and startup diagnostic logging.
 */
@Component
@Slf4j
public class HttpsTransactionalEmailClient implements TransactionalEmailClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String fromEmail;
    private final String apiUrl;
    private final Environment environment;

    @org.springframework.beans.factory.annotation.Autowired
    public HttpsTransactionalEmailClient(
            RestClient.Builder restClientBuilder,
            @Value("${klickit.mail.api-key:}") String apiKey,
            @Value("${klickit.mail.from:onboarding@resend.dev}") String fromEmail,
            @Value("${klickit.mail.api-url:https://api.resend.com/emails}") String apiUrl,
            @org.springframework.beans.factory.annotation.Autowired(required = false) Environment environment) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.fromEmail = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail.trim() : "onboarding@resend.dev";
        this.apiUrl = (apiUrl != null && !apiUrl.isBlank()) ? apiUrl.trim() : "https://api.resend.com/emails";
        this.environment = environment;
    }

    public HttpsTransactionalEmailClient(
            RestClient.Builder restClientBuilder,
            String apiKey,
            String fromEmail,
            String apiUrl) {
        this(restClientBuilder, apiKey, fromEmail, apiUrl, null);
    }

    public HttpsTransactionalEmailClient(
            RestClient restClient,
            String apiKey,
            String fromEmail,
            String apiUrl) {
        this(restClient, apiKey, fromEmail, apiUrl, null);
    }

    public HttpsTransactionalEmailClient(
            RestClient restClient,
            String apiKey,
            String fromEmail,
            String apiUrl,
            Environment environment) {
        this.restClient = restClient;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.fromEmail = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail.trim() : "onboarding@resend.dev";
        this.apiUrl = (apiUrl != null && !apiUrl.isBlank()) ? apiUrl.trim() : "https://api.resend.com/emails";
        this.environment = environment;
    }

    @PostConstruct
    public void validateConfigurationOnStartup() {
        boolean isProd = environment != null && Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (apiKey.isEmpty()) {
            if (isProd) {
                log.error("=========================================================================================");
                log.error("CRITICAL PRODUCTION CONFIGURATION ERROR: MAIL_API_KEY is not configured!");
                log.error("In production profile 'prod', transactional email delivery is mandatory.");
                log.error("Without MAIL_API_KEY, customer OTP login codes and admin order alerts cannot be dispatched.");
                log.error("=========================================================================================");
                throw new IllegalStateException("MAIL_API_KEY must be configured when running under 'prod' profile");
            }
            log.warn("MAIL_API_KEY is not configured. Transactional email notifications will be skipped.");
        } else {
            log.info("HttpsTransactionalEmailClient initialized successfully with endpoint: [{}] and sender: [{}]", apiUrl, fromEmail);
        }

        if (fromEmail.isBlank()) {
            if (isProd) {
                throw new IllegalStateException("MAIL_FROM must be configured when running under 'prod' profile");
            }
            log.warn("MAIL_FROM is not configured.");
        }
    }

    @Override
    public boolean sendEmail(String to, String subject, String htmlBody) {
        if (apiKey.isEmpty()) {
            log.warn("Cannot dispatch transactional email to [{}]: MAIL_API_KEY is missing or empty. Skipping email notification.", to);
            return false;
        }

        Map<String, Object> payload = Map.of(
                "from", fromEmail,
                "to", List.of(to),
                "subject", subject,
                "html", htmlBody
        );

        // Attempt 1
        boolean success = executePost(to, payload);
        if (success) {
            return true;
        }

        // Retry once after ~1s delay
        log.warn("Retrying transactional email delivery to [{}] after 1s delay...", to);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("Interrupted during email retry backoff for [{}]", to);
            return false;
        }

        return executePost(to, payload);
    }

    private boolean executePost(String to, Map<String, Object> payload) {
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Transactional email successfully accepted by provider for recipient: [{}]", to);
                return true;
            } else {
                log.error("Transactional email provider returned non-2xx status: [{}] for recipient: [{}]",
                        response.getStatusCode(), to);
                return false;
            }
        } catch (HttpStatusCodeException ex) {
            log.error("Transactional email provider rejected request for recipient: [{}]. Status: [{}]. Response: [{}]",
                    to, ex.getStatusCode(), ex.getResponseBodyAsString());
            return false;
        } catch (Exception ex) {
            log.error("Transactional email delivery failed for recipient: [{}]. Error: [{}]",
                    to, ex.getMessage());
            return false;
        }
    }
}
