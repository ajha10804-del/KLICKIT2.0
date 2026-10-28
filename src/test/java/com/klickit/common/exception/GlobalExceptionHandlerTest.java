package com.klickit.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.klickit.common.dto.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        if (logger != null && listAppender != null) {
            logger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    @Test
    @DisplayName("handleGeneral logs safe metadata and never attaches throwable or leaks credentials to logs or response")
    void handleGeneral_withRequest_logsSafeMetadataWithoutSensitiveLeakage() {
        String fakeDbUrl = "postgresql://test_user:SuperSecretTestPassword999@test-cluster.dpg.render.com:5432/test_db";
        String fakeBearer = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.doNotLeakThisSignature";
        String fakeJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiYWRtaW4ifQ.secretSignature123";
        String fakeApiKey = "re_testSecretApiKey1234567890";
        String fakeOtp = "847291";
        String fakePasswordParam = "password=SuperConfidentialPassword123!";

        // Construct deeply nested exception chain with sensitive values at every level
        SQLException rootSqlException = new SQLException("Connection refused: " + fakeDbUrl);
        IllegalStateException middleException = new IllegalStateException(
                "Failed auth with " + fakeBearer + " and jwt=" + fakeJwt, rootSqlException);
        RuntimeException topException = new RuntimeException(
                "Server failure with " + fakeApiKey + " and otp=" + fakeOtp + " and " + fakePasswordParam, middleException);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(topException, request);

        // 1. Verify HTTP Response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getData()).isNull();

        // Ensure response message never contains sensitive values
        assertThat(response.getBody().getMessage()).doesNotContain("SuperSecretTestPassword999");
        assertThat(response.getBody().getMessage()).doesNotContain("test-cluster.dpg.render.com");
        assertThat(response.getBody().getMessage()).doesNotContain("doNotLeakThisSignature");
        assertThat(response.getBody().getMessage()).doesNotContain("secretSignature123");
        assertThat(response.getBody().getMessage()).doesNotContain("re_testSecretApiKey1234567890");
        assertThat(response.getBody().getMessage()).doesNotContain("847291");
        assertThat(response.getBody().getMessage()).doesNotContain("SuperConfidentialPassword123!");

        // 2. Verify Log Output via Logback ListAppender
        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent logEvent = listAppender.list.get(0);

        assertThat(logEvent.getLevel()).isEqualTo(Level.ERROR);

        // Crucial security check: The raw Throwable is NEVER passed to log.error,
        // so Logback's ThrowableProxy must be null.
        assertThat(logEvent.getThrowableProxy())
                .as("Throwable must not be attached to log event to prevent stack trace leakage")
                .isNull();

        String loggedMessage = logEvent.getFormattedMessage();

        // Verify safe metadata is present
        assertThat(loggedMessage).contains("Unhandled server exception:");
        assertThat(loggedMessage).contains("[errorId=");
        assertThat(loggedMessage).contains("[type=java.lang.RuntimeException]");
        assertThat(loggedMessage).contains("[rootCause=java.sql.SQLException]");
        assertThat(loggedMessage).contains("[method=POST]");
        assertThat(loggedMessage).contains("[uri=/api/v1/auth/login]");
        assertThat(loggedMessage).contains("[location=");

        // Crucial security check: Log message contains NO sensitive credentials or payload data
        assertThat(loggedMessage).doesNotContain("SuperSecretTestPassword999");
        assertThat(loggedMessage).doesNotContain("test-cluster.dpg.render.com");
        assertThat(loggedMessage).doesNotContain("doNotLeakThisSignature");
        assertThat(loggedMessage).doesNotContain("secretSignature123");
        assertThat(loggedMessage).doesNotContain("re_testSecretApiKey1234567890");
        assertThat(loggedMessage).doesNotContain("847291");
        assertThat(loggedMessage).doesNotContain("SuperConfidentialPassword123!");
        assertThat(loggedMessage).doesNotContain("postgresql://");
        assertThat(loggedMessage).doesNotContain("Connection refused");
    }

    @Test
    @DisplayName("handleGeneral without HttpServletRequest handles null request safely")
    void handleGeneral_withoutRequest_logsSafeFallback() {
        Exception ex = new RuntimeException("Database timeout: postgresql://admin:secret@host/db");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");

        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent logEvent = listAppender.list.get(0);
        assertThat(logEvent.getThrowableProxy()).isNull();

        String loggedMessage = logEvent.getFormattedMessage();
        assertThat(loggedMessage).contains("[method=N/A]");
        assertThat(loggedMessage).contains("[uri=N/A]");
        assertThat(loggedMessage).contains("[type=java.lang.RuntimeException]");
        assertThat(loggedMessage).doesNotContain("secret");
        assertThat(loggedMessage).doesNotContain("postgresql://");
    }

    @Test
    @DisplayName("handleGeneral handles null exception gracefully")
    void handleGeneral_nullException_handlesGracefully() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");

        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent logEvent = listAppender.list.get(0);
        assertThat(logEvent.getThrowableProxy()).isNull();

        String loggedMessage = logEvent.getFormattedMessage();
        assertThat(loggedMessage).contains("[type=UnknownException]");
        assertThat(loggedMessage).contains("[rootCause=None]");
        assertThat(loggedMessage).contains("[location=unknown]");
    }

    @Test
    @DisplayName("sanitizeForLogging redacts database credentials in URLs")
    void sanitizeForLogging_redactsDatabaseCredentials() {
        String raw = "Failed to connect to postgresql://klickit_db_user:6193RurOAOb7IInA9QfA75E4p9c4CadU@dpg-db6ernjrjlhs7388kmu0-a/klickit_db";
        String sanitized = GlobalExceptionHandler.sanitizeForLogging(raw);

        assertThat(sanitized).doesNotContain("6193RurOAOb7IInA9QfA75E4p9c4CadU");
        assertThat(sanitized).contains("[REDACTED_PASSWORD]");
        assertThat(sanitized).contains("[REDACTED_USER]");
    }

    @Test
    @DisplayName("sanitizeForLogging redacts Bearer tokens and Authorization headers")
    void sanitizeForLogging_redactsBearerTokens() {
        String raw = "Request failed with header Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.t-IDcSemACt8x4iTMCda8Yhe3iZaWbvV5XKSTbuAn0M";
        String sanitized = GlobalExceptionHandler.sanitizeForLogging(raw);

        assertThat(sanitized).doesNotContain("t-IDcSemACt8x4iTMCda8Yhe3iZaWbvV5XKSTbuAn0M");
        assertThat(sanitized).contains("Bearer [REDACTED]");
    }

    @Test
    @DisplayName("sanitizeForLogging redacts passwords and secrets in key-value pairs")
    void sanitizeForLogging_redactsPasswordParameters() {
        String raw = "Auth error: password=SecretPass! and apiKey=re_1234567890abcdef and secret = 'MyJwtSecret123'";
        String sanitized = GlobalExceptionHandler.sanitizeForLogging(raw);

        assertThat(sanitized).doesNotContain("SecretPass!");
        assertThat(sanitized).doesNotContain("MyJwtSecret123");
        assertThat(sanitized).contains("password=[REDACTED]");
        assertThat(sanitized).contains("secret=[REDACTED]");
    }

    @Test
    @DisplayName("sanitizeForLogging redacts OTP values")
    void sanitizeForLogging_redactsOtpCodes() {
        String raw = "Delivery failed for code=123456 and otp: 654321";
        String sanitized = GlobalExceptionHandler.sanitizeForLogging(raw);

        assertThat(sanitized).doesNotContain("123456");
        assertThat(sanitized).doesNotContain("654321");
        assertThat(sanitized).contains("code=[REDACTED]");
        assertThat(sanitized).contains("otp=[REDACTED]");
    }

    @Test
    @DisplayName("sanitizeForLogging handles null and blank messages safely")
    void sanitizeForLogging_handlesNullAndBlank() {
        assertThat(GlobalExceptionHandler.sanitizeForLogging(null)).isEmpty();
        assertThat(GlobalExceptionHandler.sanitizeForLogging("   ")).isEmpty();
    }

    @Test
    @DisplayName("Non-user DataIntegrityViolationException falls through to handleGeneral and does not return account message")
    void handleDataIntegrityViolation_nonUserConstraint_returnsGenericUnexpectedError() {
        org.springframework.dao.DataIntegrityViolationException ex =
                new org.springframework.dao.DataIntegrityViolationException("duplicate key value violates unique constraint 'orders_pkey'");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getMessage()).doesNotContain("account");
        assertThat(response.getBody().getMessage()).doesNotContain("An account with these details already exists");
    }
}
