package com.klickit.user;

import com.klickit.common.exception.InvalidOtpException;
import com.klickit.common.exception.OtpRateLimitException;
import com.klickit.config.JwtService;
import com.klickit.notification.service.EmailService;
import com.klickit.user.dto.AuthResponse;
import com.klickit.user.entity.OtpCode;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.OtpCodeRepository;
import com.klickit.user.repository.UserRepository;
import com.klickit.user.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final String TEST_SECRET = "test-secret-key-for-otp-unit-tests-hmac-sha256-12345";
    private static final String TEST_EMAIL = "customer@example.com";

    @Mock
    private OtpCodeRepository otpCodeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpService, "enabled", true);
        ReflectionTestUtils.setField(otpService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(otpService, "ttlMinutes", 5L);
        ReflectionTestUtils.setField(otpService, "maxAttempts", 5);
        ReflectionTestUtils.setField(otpService, "resendCooldownSeconds", 60L);
        ReflectionTestUtils.setField(otpService, "maxRequestsPerHour", 5);
        ReflectionTestUtils.setField(otpService, "devLogCode", false);
        org.mockito.Mockito.lenient().when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);
        User defaultTestUser = User.builder()
                .name("Test User")
                .email(TEST_EMAIL)
                .role(Role.CUSTOMER)
                .enabled(true)
                .build();
        org.mockito.Mockito.lenient().when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(defaultTestUser));
    }

    private String computeHmac(String email, String code) throws Exception {
        return computeHmac(email, "LOGIN", code);
    }

    private String computeHmac(String email, String purpose, String code) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String normPurpose = (purpose != null && !purpose.isBlank()) ? purpose.trim().toUpperCase() : "LOGIN";
        return HexFormat.of().formatHex(mac.doFinal((email.toLowerCase().trim() + ":" + normPurpose + ":" + code.trim()).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Startup validation fails fast if secret is empty when enabled")
    void startup_failsFastWhenSecretMissing() {
        ReflectionTestUtils.setField(otpService, "secret", "");
        assertThatThrownBy(() -> otpService.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OTP_SECRET must be configured");
    }

    @Test
    @DisplayName("Request OTP generates digest, invalidates active codes, and dispatches email")
    void requestOtp_success() {
        when(otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(TEST_EMAIL)).thenReturn(Optional.empty());
        when(otpCodeRepository.countByEmailAndCreatedAtAfter(eq(TEST_EMAIL), any(Instant.class))).thenReturn(0L);

        otpService.requestOtp(TEST_EMAIL);

        verify(otpCodeRepository).invalidateActive(eq(TEST_EMAIL), any(Instant.class));

        ArgumentCaptor<OtpCode> captor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepository).save(captor.capture());

        OtpCode saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(saved.getCodeHash()).isNotNull().hasSize(64); // 256 bits = 64 hex chars
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getConsumedAt()).isNull();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());

        verify(emailService).sendOtpEmail(eq(TEST_EMAIL), anyString(), eq(OtpService.PURPOSE_LOGIN), eq(5L));
    }

    @Test
    @DisplayName("Request OTP enforces 60-second cooldown")
    void requestOtp_cooldownEnforced() {
        OtpCode recentOtp = OtpCode.builder()
                .email(TEST_EMAIL)
                .codeHash("dummy")
                .build();
        recentOtp.setCreatedAt(Instant.now().minusSeconds(10));
        when(otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(TEST_EMAIL)).thenReturn(Optional.of(recentOtp));

        assertThatThrownBy(() -> otpService.requestOtp(TEST_EMAIL))
                .isInstanceOf(OtpRateLimitException.class)
                .hasMessageContaining("wait before requesting another");

        verify(otpCodeRepository, never()).save(any());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Request OTP enforces hourly request limit (max 5)")
    void requestOtp_hourlyLimitEnforced() {
        when(otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(TEST_EMAIL)).thenReturn(Optional.empty());
        when(otpCodeRepository.countByEmailAndCreatedAtAfter(eq(TEST_EMAIL), any(Instant.class))).thenReturn(5L);

        assertThatThrownBy(() -> otpService.requestOtp(TEST_EMAIL))
                .isInstanceOf(OtpRateLimitException.class)
                .hasMessageContaining("Too many sign-in code requests");

        verify(otpCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Request OTP on email delivery failure removes unusable code without trapping customer")
    void requestOtp_emailFailureDeletesUnusableOtp() {
        when(otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(TEST_EMAIL)).thenReturn(Optional.empty());
        when(otpCodeRepository.countByEmailAndCreatedAtAfter(eq(TEST_EMAIL), any(Instant.class))).thenReturn(0L);
        doThrow(new IllegalStateException("Provider delivery failed"))
                .when(emailService).sendOtpEmail(eq(TEST_EMAIL), anyString(), eq(OtpService.PURPOSE_LOGIN), eq(5L));

        // Throws IllegalStateException with generic message to prevent misleading success response
        assertThatThrownBy(() -> otpService.requestOtp(TEST_EMAIL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("We couldn't send the code. Please try again.");

        verify(otpCodeRepository).save(any(OtpCode.class));
        verify(otpCodeRepository).delete(any(OtpCode.class));
    }

    @Test
    @DisplayName("Concurrent OTP requests for the same email: only one succeeds, others fail rate-limit cooldown")
    void requestOtp_concurrencySerializedPerEmail() throws Exception {
        when(otpCodeRepository.countByEmailAndCreatedAtAfter(eq(TEST_EMAIL), any(Instant.class))).thenReturn(0L);

        AtomicInteger lookupCount = new AtomicInteger(0);
        OtpCode createdOtp = OtpCode.builder()
                .email(TEST_EMAIL)
                .codeHash("hash")
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(0)
                .build();
        createdOtp.setCreatedAt(Instant.now());

        when(otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(TEST_EMAIL))
                .thenAnswer(invocation -> {
                    if (lookupCount.getAndIncrement() == 0) {
                        return Optional.empty();
                    } else {
                        return Optional.of(createdOtp);
                    }
                });

        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rateLimitedCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    otpService.requestOtp(TEST_EMAIL);
                    successCount.incrementAndGet();
                } catch (OtpRateLimitException e) {
                    rateLimitedCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(rateLimitedCount.get()).isEqualTo(numThreads - 1);
        verify(emailService, times(1)).sendOtpEmail(eq(TEST_EMAIL), anyString(), eq(OtpService.PURPOSE_LOGIN), anyLong());
    }

    @Test
    @DisplayName("Verify OTP succeeds for existing CUSTOMER and marks code consumed")
    void verifyOtp_successExistingCustomer() throws Exception {
        String code = "123456";
        String hash = computeHmac(TEST_EMAIL, code);

        OtpCode otpCode = OtpCode.builder()
                .email(TEST_EMAIL)
                .codeHash(hash)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(0)
                .build();
        otpCode.setId(UUID.randomUUID());
        otpCode.setCreatedAt(Instant.now());
        otpCode.setUpdatedAt(Instant.now());

        User existingUser = User.builder()
                .name("Alice")
                .email(TEST_EMAIL)
                .phone("9876543210")
                .role(Role.CUSTOMER)
                .build();

        when(otpCodeRepository.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL))
                .thenReturn(Optional.of(otpCode));
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(existingUser));
        when(jwtService.generateToken(existingUser)).thenReturn("jwt.token.customer");

        AuthResponse response = otpService.verifyOtp(TEST_EMAIL, code);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt.token.customer");
        assertThat(response.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(response.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(otpCode.getConsumedAt()).isNotNull();

        verify(otpCodeRepository).save(otpCode);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Verify OTP rejects ADMIN role even with a valid pre-existing code")
    void verifyOtp_adminRole_rejectedWithInvalidOtpException() throws Exception {
        String adminEmail = "admin@klickit.com";
        String code = "654321";
        String hash = computeHmac(adminEmail, code);

        OtpCode otpCode = OtpCode.builder()
                .email(adminEmail)
                .codeHash(hash)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(0)
                .build();
        otpCode.setId(UUID.randomUUID());
        otpCode.setCreatedAt(Instant.now());
        otpCode.setUpdatedAt(Instant.now());

        User adminUser = User.builder()
                .name("Admin User")
                .email(adminEmail)
                .phone("9999999999")
                .role(Role.ADMIN)
                .enabled(true)
                .build();

        when(userRepository.findByEmail(adminEmail)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> otpService.verifyOtp(adminEmail, code))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Invalid or expired code");

        verify(jwtService, never()).generateToken(any());
        verify(otpCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Verify OTP rejects DELIVERY_PARTNER role even with a valid pre-existing code")
    void verifyOtp_deliveryPartnerRole_rejectedWithInvalidOtpException() throws Exception {
        String driverEmail = "driver@klickit.com";
        String code = "654321";

        User driverUser = User.builder()
                .name("Driver User")
                .email(driverEmail)
                .phone("9888888888")
                .role(Role.DELIVERY_PARTNER)
                .enabled(true)
                .build();

        when(userRepository.findByEmail(driverEmail)).thenReturn(Optional.of(driverUser));

        assertThatThrownBy(() -> otpService.verifyOtp(driverEmail, code))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessage("Invalid or expired code");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("Verify OTP for unknown email throws InvalidOtpException and never creates an account")
    void verifyOtp_unknownEmail_neverCreatesAccount() {
        String unknownEmail = "unknown@example.com";
        String code = "112233";

        when(userRepository.findByEmail(unknownEmail)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verifyOtp(unknownEmail, code))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("Invalid or expired code");

        verify(userRepository, never()).save(any(User.class));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("Request OTP for unknown email silently skips without sending email or creating OtpCode")
    void requestOtp_unknownEmail_silentlySkipped() {
        String unknownEmail = "ghost@example.com";
        when(userRepository.findByEmail(unknownEmail)).thenReturn(Optional.empty());

        otpService.requestOtp(unknownEmail);

        verify(otpCodeRepository, never()).save(any());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Request OTP for ADMIN email silently skips without sending email or creating OtpCode")
    void requestOtp_adminEmail_silentlySkipped() {
        String adminEmail = "admin@klickit.com";
        User adminUser = User.builder()
                .name("Admin")
                .email(adminEmail)
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        when(userRepository.findByEmail(adminEmail)).thenReturn(Optional.of(adminUser));

        otpService.requestOtp(adminEmail);

        verify(otpCodeRepository, never()).save(any());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Request OTP for DELIVERY_PARTNER email silently skips without sending email or creating OtpCode")
    void requestOtp_deliveryPartnerEmail_silentlySkipped() {
        String driverEmail = "driver@klickit.com";
        User driverUser = User.builder()
                .name("Driver")
                .email(driverEmail)
                .role(Role.DELIVERY_PARTNER)
                .enabled(true)
                .build();
        when(userRepository.findByEmail(driverEmail)).thenReturn(Optional.of(driverUser));

        otpService.requestOtp(driverEmail);

        verify(otpCodeRepository, never()).save(any());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Verify OTP rejects disabled account with DisabledException")
    void verifyOtp_disabledAccountRejected() throws Exception {
        String code = "123456";
        String hash = computeHmac(TEST_EMAIL, "LOGIN", code);

        OtpCode otpCode = OtpCode.builder()
                .email(TEST_EMAIL)
                .codeHash(hash)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(0)
                .build();
        otpCode.setId(UUID.randomUUID());
        otpCode.setCreatedAt(Instant.now());

        User disabledUser = User.builder()
                .name("Disabled User")
                .email(TEST_EMAIL)
                .phone("9876543210")
                .role(Role.CUSTOMER)
                .enabled(false)
                .build();

        when(otpCodeRepository.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL))
                .thenReturn(Optional.of(otpCode));
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(disabledUser));

        assertThatThrownBy(() -> otpService.verifyOtp(TEST_EMAIL, code))
                .isInstanceOf(org.springframework.security.authentication.DisabledException.class)
                .hasMessageContaining("Account is disabled");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("Purpose separation: registration code is rejected at login verify")
    void purposeSeparation_registrationCodeRejectedAtLoginVerify() throws Exception {
        String code = "123456";
        // OTP was generated for REGISTRATION
        String registrationHash = computeHmac(TEST_EMAIL, "REGISTRATION", code);

        OtpCode otpCode = OtpCode.builder()
                .email(TEST_EMAIL)
                .codeHash(registrationHash)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(0)
                .build();
        otpCode.setId(UUID.randomUUID());
        otpCode.setCreatedAt(Instant.now());

        User existingUser = User.builder()
                .name("Alice")
                .email(TEST_EMAIL)
                .phone("9876543210")
                .role(Role.CUSTOMER)
                .enabled(true)
                .build();

        when(otpCodeRepository.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL))
                .thenReturn(Optional.of(otpCode));
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(existingUser));

        // When submitted to verifyOtp (which uses PURPOSE_LOGIN), hash mismatch occurs
        assertThatThrownBy(() -> otpService.verifyOtp(TEST_EMAIL, code))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("Invalid or expired code");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("Purpose separation: login code is rejected at registration verify")
    void purposeSeparation_loginCodeRejectedAtRegistrationVerify() throws Exception {
        String code = "123456";
        // OTP was generated for LOGIN
        String loginHash = computeHmac(TEST_EMAIL, "LOGIN", code);

        OtpCode otpCode = OtpCode.builder()
                .email(TEST_EMAIL)
                .codeHash(loginHash)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(0)
                .build();
        otpCode.setId(UUID.randomUUID());
        otpCode.setCreatedAt(Instant.now());

        when(otpCodeRepository.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL))
                .thenReturn(Optional.of(otpCode));

        // When submitted with PURPOSE_REGISTRATION, hash mismatch occurs
        assertThatThrownBy(() -> otpService.verifyAndConsumeOtp(TEST_EMAIL, code, OtpService.PURPOSE_REGISTRATION))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("Invalid or expired code");
    }

    @Test
    @DisplayName("Verify OTP throws InvalidOtpException for expired code")
    void verifyOtp_expiredCodeThrows() throws Exception {
        String code = "123456";
        String hash = computeHmac(TEST_EMAIL, code);

        OtpCode expiredOtp = OtpCode.builder()
                .email(TEST_EMAIL)
                .codeHash(hash)
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .attempts(0)
                .build();

        when(otpCodeRepository.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL))
                .thenReturn(Optional.of(expiredOtp));

        assertThatThrownBy(() -> otpService.verifyOtp(TEST_EMAIL, code))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("Invalid or expired code");

        assertThat(expiredOtp.getConsumedAt()).isNull();
    }

    @Test
    @DisplayName("Verify OTP single-use: already consumed code cannot be reused")
    void verifyOtp_singleUseEnforced() {
        when(otpCodeRepository.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verifyOtp(TEST_EMAIL, "123456"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("Invalid or expired code");
    }

    @Test
    @DisplayName("Verify OTP increments attempt counter on incorrect code")
    void verifyOtp_incorrectCodeIncrementsAttempts() throws Exception {
        String correctCode = "123456";
        String incorrectCode = "999999";
        String hash = computeHmac(TEST_EMAIL, correctCode);

        OtpCode otpCode = OtpCode.builder()
                .email(TEST_EMAIL)
                .codeHash(hash)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(2)
                .build();
        otpCode.setCreatedAt(Instant.now());
        otpCode.setUpdatedAt(Instant.now());

        when(otpCodeRepository.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL))
                .thenReturn(Optional.of(otpCode));

        assertThatThrownBy(() -> otpService.verifyOtp(TEST_EMAIL, incorrectCode))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("Invalid or expired code");

        assertThat(otpCode.getAttempts()).isEqualTo(3);
        assertThat(otpCode.getConsumedAt()).isNull();
        verify(otpCodeRepository).save(otpCode);
    }

    @Test
    @DisplayName("Verify OTP revokes code after max attempts (5) reached")
    void verifyOtp_maxAttemptsExceeded() throws Exception {
        String correctCode = "123456";
        String hash = computeHmac(TEST_EMAIL, correctCode);

        OtpCode lockedOtp = OtpCode.builder()
                .email(TEST_EMAIL)
                .codeHash(hash)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(5)
                .build();

        when(otpCodeRepository.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL))
                .thenReturn(Optional.of(lockedOtp));

        assertThatThrownBy(() -> otpService.verifyOtp(TEST_EMAIL, correctCode))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("Invalid or expired code");
    }

    @Test
    @DisplayName("Concurrent verification attempts allow only one success")
    void verifyOtp_concurrencySafety() throws Exception {
        String code = "123456";
        String hash = computeHmac(TEST_EMAIL, code);

        OtpCode otpCode = OtpCode.builder()
                .email(TEST_EMAIL)
                .codeHash(hash)
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .attempts(0)
                .build();
        otpCode.setCreatedAt(Instant.now());
        otpCode.setUpdatedAt(Instant.now());

        User user = User.builder()
                .name("Alice")
                .email(TEST_EMAIL)
                .role(Role.CUSTOMER)
                .build();

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt.token");

        // The first lookup finds unconsumed OTP; subsequent call finds it already consumed
        AtomicInteger lookupCount = new AtomicInteger(0);
        when(otpCodeRepository.findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(TEST_EMAIL))
                .thenAnswer(invocation -> {
                    if (lookupCount.getAndIncrement() == 0) {
                        return Optional.of(otpCode);
                    } else {
                        return Optional.empty(); // Already consumed by thread 1
                    }
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    otpService.verifyOtp(TEST_EMAIL, code);
                    successCount.incrementAndGet();
                } catch (InvalidOtpException e) {
                    failCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Purge expired OTP codes delegates to repository with 24-hour cutoff")
    void purgeExpiredOtpCodes_success() {
        when(otpCodeRepository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(12L);

        otpService.purgeExpiredOtpCodes();

        verify(otpCodeRepository).deleteByExpiresAtBefore(any(Instant.class));
    }
}
