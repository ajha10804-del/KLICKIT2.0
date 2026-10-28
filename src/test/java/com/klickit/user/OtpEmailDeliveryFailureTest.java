package com.klickit.user;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.klickit.config.JwtService;
import com.klickit.notification.client.TransactionalEmailClient;
import com.klickit.notification.service.EmailService;
import com.klickit.user.dto.RegistrationOtpRequest;
import com.klickit.user.entity.OtpCode;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.OtpCodeRepository;
import com.klickit.user.repository.UserRepository;
import com.klickit.user.service.OtpService;
import com.klickit.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpEmailDeliveryFailureTest {

    private static final String TEST_EMAIL = "customer@example.com";
    private static final String SECRET_KEY = "test-otp-secret-key-must-be-long-enough-for-hmac-sha256";

    @Mock
    private OtpCodeRepository otpCodeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    private FakeTransactionalEmailClient fakeEmailClient;
    private EmailService emailService;
    private OtpService otpServiceProd;
    private OtpService otpServiceDev;
    private UserService userServiceProd;
    private UserService userServiceDev;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger otpLogger;

    enum FakeEmailOutcome {
        SUCCESS,
        RETURN_FALSE,
        RETURN_403,
        THROW_EXCEPTION
    }

    static class FakeTransactionalEmailClient implements TransactionalEmailClient {
        private FakeEmailOutcome outcome = FakeEmailOutcome.SUCCESS;
        private boolean apiKeyConfigured = true;

        void setOutcome(FakeEmailOutcome outcome) {
            this.outcome = outcome;
        }

        void setApiKeyConfigured(boolean apiKeyConfigured) {
            this.apiKeyConfigured = apiKeyConfigured;
        }

        @Override
        public boolean sendEmail(String to, String subject, String htmlBody) {
            if (!apiKeyConfigured) {
                return false;
            }
            switch (outcome) {
                case RETURN_FALSE:
                    return false;
                case RETURN_403:
                    throw new HttpStatusCodeException(HttpStatus.FORBIDDEN, "Forbidden: Invalid API key or domain not verified") {};
                case THROW_EXCEPTION:
                    throw new RuntimeException("Simulated connection timeout to transactional email provider");
                case SUCCESS:
                default:
                    return true;
            }
        }
    }

    @BeforeEach
    void setUp() {
        fakeEmailClient = new FakeTransactionalEmailClient();
        emailService = new EmailService(fakeEmailClient, "admin@klickit.com", "Asia/Kolkata");

        // Prod OTP Service (dev-log-code = false)
        otpServiceProd = new OtpService(otpCodeRepository, userRepository, emailService, jwtService, passwordEncoder);
        ReflectionTestUtils.setField(otpServiceProd, "secret", SECRET_KEY);
        ReflectionTestUtils.setField(otpServiceProd, "ttlMinutes", 5L);
        ReflectionTestUtils.setField(otpServiceProd, "maxAttempts", 5);
        ReflectionTestUtils.setField(otpServiceProd, "resendCooldownSeconds", 60L);
        ReflectionTestUtils.setField(otpServiceProd, "maxRequestsPerHour", 5);
        ReflectionTestUtils.setField(otpServiceProd, "devLogCode", false);

        // Dev OTP Service (dev-log-code = true)
        otpServiceDev = new OtpService(otpCodeRepository, userRepository, emailService, jwtService, passwordEncoder);
        ReflectionTestUtils.setField(otpServiceDev, "secret", SECRET_KEY);
        ReflectionTestUtils.setField(otpServiceDev, "ttlMinutes", 5L);
        ReflectionTestUtils.setField(otpServiceDev, "maxAttempts", 5);
        ReflectionTestUtils.setField(otpServiceDev, "resendCooldownSeconds", 60L);
        ReflectionTestUtils.setField(otpServiceDev, "maxRequestsPerHour", 5);
        ReflectionTestUtils.setField(otpServiceDev, "devLogCode", true);

        userServiceProd = new UserService(userRepository, passwordEncoder, jwtService, authenticationManager, otpServiceProd);
        userServiceDev = new UserService(userRepository, passwordEncoder, jwtService, authenticationManager, otpServiceDev);

        otpLogger = (Logger) LoggerFactory.getLogger(OtpService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        otpLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        if (otpLogger != null && listAppender != null) {
            otpLogger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    private void mockRegisteredCustomer() {
        User user = User.builder()
                .name("Customer")
                .email(TEST_EMAIL)
                .role(Role.CUSTOMER)
                .enabled(true)
                .build();
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
    }

    // =========================================================================
    // 1. EmailService.sendOtpEmail failure behavior
    // =========================================================================

    @Test
    @DisplayName("EmailService.sendOtpEmail throws IllegalStateException when client returns false")
    void sendOtpEmail_clientReturnsFalse_throwsIllegalStateException() {
        fakeEmailClient.setOutcome(FakeEmailOutcome.RETURN_FALSE);

        assertThatThrownBy(() -> emailService.sendOtpEmail(TEST_EMAIL, "123456", OtpService.PURPOSE_LOGIN, 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email delivery failed");
    }

    @Test
    @DisplayName("EmailService.sendOtpEmail throws IllegalStateException when provider returns 403")
    void sendOtpEmail_providerReturns403_throwsIllegalStateException() {
        fakeEmailClient.setOutcome(FakeEmailOutcome.RETURN_403);

        assertThatThrownBy(() -> emailService.sendOtpEmail(TEST_EMAIL, "123456", OtpService.PURPOSE_LOGIN, 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transactional email provider error");
    }

    @Test
    @DisplayName("EmailService.sendOtpEmail throws IllegalStateException when provider throws exception")
    void sendOtpEmail_providerThrows_throwsIllegalStateException() {
        fakeEmailClient.setOutcome(FakeEmailOutcome.THROW_EXCEPTION);

        assertThatThrownBy(() -> emailService.sendOtpEmail(TEST_EMAIL, "123456", OtpService.PURPOSE_LOGIN, 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transactional email provider error");
    }

    // =========================================================================
    // 2. Prod Configuration (dev-log-code=false): Login requests on failure
    //    - Must throw generic 4xx message ("We couldn't send the code. Please try again.")
    //    - Must leave no OtpCode row in DB (deleted)
    //    - Must never log the code
    // =========================================================================

    @Test
    @DisplayName("PROD Login OTP: provider returns false -> 4xx generic message, deletes OtpCode, never logs code")
    void prodLoginOtp_providerReturnsFalse() {
        mockRegisteredCustomer();
        fakeEmailClient.setOutcome(FakeEmailOutcome.RETURN_FALSE);

        List<OtpCode> storedCodes = Collections.synchronizedList(new ArrayList<>());
        when(otpCodeRepository.save(any(OtpCode.class))).thenAnswer(inv -> {
            OtpCode code = inv.getArgument(0);
            storedCodes.add(code);
            return code;
        });
        doAnswer(inv -> {
            OtpCode code = inv.getArgument(0);
            storedCodes.remove(code);
            return null;
        }).when(otpCodeRepository).delete(any(OtpCode.class));

        assertThatThrownBy(() -> otpServiceProd.requestOtp(TEST_EMAIL, OtpService.PURPOSE_LOGIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("We couldn't send the code. Please try again.");

        assertThat(storedCodes).isEmpty();
        verify(otpCodeRepository).delete(any(OtpCode.class));

        // Assert that no log message contains a 6-digit numeric OTP code
        for (ILoggingEvent event : listAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotMatch(".*\\b\\d{6}\\b.*");
        }
    }

    @Test
    @DisplayName("PROD Login OTP: provider returns 403 -> 4xx generic message, deletes OtpCode, never logs code")
    void prodLoginOtp_providerReturns403() {
        mockRegisteredCustomer();
        fakeEmailClient.setOutcome(FakeEmailOutcome.RETURN_403);

        List<OtpCode> storedCodes = Collections.synchronizedList(new ArrayList<>());
        when(otpCodeRepository.save(any(OtpCode.class))).thenAnswer(inv -> {
            OtpCode code = inv.getArgument(0);
            storedCodes.add(code);
            return code;
        });
        doAnswer(inv -> {
            OtpCode code = inv.getArgument(0);
            storedCodes.remove(code);
            return null;
        }).when(otpCodeRepository).delete(any(OtpCode.class));

        assertThatThrownBy(() -> otpServiceProd.requestOtp(TEST_EMAIL, OtpService.PURPOSE_LOGIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("We couldn't send the code. Please try again.");

        assertThat(storedCodes).isEmpty();
        verify(otpCodeRepository).delete(any(OtpCode.class));

        for (ILoggingEvent event : listAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotMatch(".*\\b\\d{6}\\b.*");
        }
    }

    @Test
    @DisplayName("PROD Login OTP: provider throws -> 4xx generic message, deletes OtpCode, never logs code")
    void prodLoginOtp_providerThrows() {
        mockRegisteredCustomer();
        fakeEmailClient.setOutcome(FakeEmailOutcome.THROW_EXCEPTION);

        List<OtpCode> storedCodes = Collections.synchronizedList(new ArrayList<>());
        when(otpCodeRepository.save(any(OtpCode.class))).thenAnswer(inv -> {
            OtpCode code = inv.getArgument(0);
            storedCodes.add(code);
            return code;
        });
        doAnswer(inv -> {
            OtpCode code = inv.getArgument(0);
            storedCodes.remove(code);
            return null;
        }).when(otpCodeRepository).delete(any(OtpCode.class));

        assertThatThrownBy(() -> otpServiceProd.requestOtp(TEST_EMAIL, OtpService.PURPOSE_LOGIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("We couldn't send the code. Please try again.");

        assertThat(storedCodes).isEmpty();
        verify(otpCodeRepository).delete(any(OtpCode.class));

        for (ILoggingEvent event : listAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotMatch(".*\\b\\d{6}\\b.*");
        }
    }

    // =========================================================================
    // 3. Prod Configuration (dev-log-code=false): Registration requests on failure
    //    - Must throw generic 4xx message ("We couldn't send the code. Please try again.")
    //    - Must leave no OtpCode row in DB (deleted)
    //    - Must never log the code
    // =========================================================================

    @Test
    @DisplayName("PROD Registration OTP: provider returns false -> 4xx generic message, deletes OtpCode, never logs code")
    void prodRegistrationOtp_providerReturnsFalse() {
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(userRepository.existsByPhone("9876543210")).thenReturn(false);
        fakeEmailClient.setOutcome(FakeEmailOutcome.RETURN_FALSE);

        List<OtpCode> storedCodes = Collections.synchronizedList(new ArrayList<>());
        when(otpCodeRepository.save(any(OtpCode.class))).thenAnswer(inv -> {
            OtpCode code = inv.getArgument(0);
            storedCodes.add(code);
            return code;
        });
        doAnswer(inv -> {
            OtpCode code = inv.getArgument(0);
            storedCodes.remove(code);
            return null;
        }).when(otpCodeRepository).delete(any(OtpCode.class));

        RegistrationOtpRequest req = new RegistrationOtpRequest("John Doe", TEST_EMAIL, "9876543210", "SecretPassword123");

        assertThatThrownBy(() -> userServiceProd.requestRegistrationOtp(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("We couldn't send the code. Please try again.");

        assertThat(storedCodes).isEmpty();
        verify(otpCodeRepository).delete(any(OtpCode.class));

        for (ILoggingEvent event : listAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotMatch(".*\\b\\d{6}\\b.*");
        }
    }

    @Test
    @DisplayName("PROD Registration OTP: provider returns 403 -> 4xx generic message, deletes OtpCode, never logs code")
    void prodRegistrationOtp_providerReturns403() {
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(userRepository.existsByPhone("9876543210")).thenReturn(false);
        fakeEmailClient.setOutcome(FakeEmailOutcome.RETURN_403);

        List<OtpCode> storedCodes = Collections.synchronizedList(new ArrayList<>());
        when(otpCodeRepository.save(any(OtpCode.class))).thenAnswer(inv -> {
            OtpCode code = inv.getArgument(0);
            storedCodes.add(code);
            return code;
        });
        doAnswer(inv -> {
            OtpCode code = inv.getArgument(0);
            storedCodes.remove(code);
            return null;
        }).when(otpCodeRepository).delete(any(OtpCode.class));

        RegistrationOtpRequest req = new RegistrationOtpRequest("John Doe", TEST_EMAIL, "9876543210", "SecretPassword123");

        assertThatThrownBy(() -> userServiceProd.requestRegistrationOtp(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("We couldn't send the code. Please try again.");

        assertThat(storedCodes).isEmpty();
        verify(otpCodeRepository).delete(any(OtpCode.class));

        for (ILoggingEvent event : listAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotMatch(".*\\b\\d{6}\\b.*");
        }
    }

    @Test
    @DisplayName("PROD Registration OTP: provider throws -> 4xx generic message, deletes OtpCode, never logs code")
    void prodRegistrationOtp_providerThrows() {
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(userRepository.existsByPhone("9876543210")).thenReturn(false);
        fakeEmailClient.setOutcome(FakeEmailOutcome.THROW_EXCEPTION);

        List<OtpCode> storedCodes = Collections.synchronizedList(new ArrayList<>());
        when(otpCodeRepository.save(any(OtpCode.class))).thenAnswer(inv -> {
            OtpCode code = inv.getArgument(0);
            storedCodes.add(code);
            return code;
        });
        doAnswer(inv -> {
            OtpCode code = inv.getArgument(0);
            storedCodes.remove(code);
            return null;
        }).when(otpCodeRepository).delete(any(OtpCode.class));

        RegistrationOtpRequest req = new RegistrationOtpRequest("John Doe", TEST_EMAIL, "9876543210", "SecretPassword123");

        assertThatThrownBy(() -> userServiceProd.requestRegistrationOtp(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("We couldn't send the code. Please try again.");

        assertThat(storedCodes).isEmpty();
        verify(otpCodeRepository).delete(any(OtpCode.class));

        for (ILoggingEvent event : listAppender.list) {
            assertThat(event.getFormattedMessage()).doesNotMatch(".*\\b\\d{6}\\b.*");
        }
    }

    // =========================================================================
    // 4. Dev Configuration (dev-log-code=true): Missing API key
    //    - Request succeeds without error
    //    - OtpCode row is persisted (never deleted)
    //    - The code line is logged in the format:
    //      "[DEV ONLY] <PURPOSE> OTP code for <email>: <code_value> (email delivery skipped: local dev mode)"
    // =========================================================================

    @Test
    @DisplayName("DEV Login OTP: missing API key -> succeeds, stores OtpCode, logs the code line")
    void devLoginOtp_noApiKey_succeedsAndLogsCode() {
        mockRegisteredCustomer();
        fakeEmailClient.setApiKeyConfigured(false);

        List<OtpCode> storedCodes = new ArrayList<>();
        when(otpCodeRepository.save(any(OtpCode.class))).thenAnswer(inv -> {
            OtpCode code = inv.getArgument(0);
            storedCodes.add(code);
            return code;
        });

        // Request succeeds without exception
        otpServiceDev.requestOtp(TEST_EMAIL, OtpService.PURPOSE_LOGIN);

        // Code row persisted and never deleted
        assertThat(storedCodes).hasSize(1);
        verify(otpCodeRepository, never()).delete(any(OtpCode.class));

        // Code line is logged
        boolean foundDevLog = listAppender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("[DEV ONLY] LOGIN OTP code for " + TEST_EMAIL)
                        && e.getFormattedMessage().contains("(email delivery skipped: local dev mode)"));
        assertThat(foundDevLog).isTrue();
    }

    @Test
    @DisplayName("DEV Registration OTP: missing API key -> succeeds, stores OtpCode, logs the code line")
    void devRegistrationOtp_noApiKey_succeedsAndLogsCode() {
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(userRepository.existsByPhone("9876543210")).thenReturn(false);
        fakeEmailClient.setApiKeyConfigured(false);

        List<OtpCode> storedCodes = new ArrayList<>();
        when(otpCodeRepository.save(any(OtpCode.class))).thenAnswer(inv -> {
            OtpCode code = inv.getArgument(0);
            storedCodes.add(code);
            return code;
        });

        RegistrationOtpRequest req = new RegistrationOtpRequest("John Doe", TEST_EMAIL, "9876543210", "SecretPassword123");

        // Request succeeds without exception
        userServiceDev.requestRegistrationOtp(req);

        // Code row persisted and never deleted
        assertThat(storedCodes).hasSize(1);
        verify(otpCodeRepository, never()).delete(any(OtpCode.class));

        // Code line is logged
        boolean foundDevLog = listAppender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("[DEV ONLY] REGISTRATION OTP code for " + TEST_EMAIL)
                        && e.getFormattedMessage().contains("(email delivery skipped: local dev mode)"));
        assertThat(foundDevLog).isTrue();
    }
}
