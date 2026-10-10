package com.klickit.user;

import com.klickit.common.exception.InvalidOtpException;
import com.klickit.common.exception.OtpRateLimitException;
import com.klickit.notification.service.EmailService;
import com.klickit.user.dto.AuthResponse;
import com.klickit.user.entity.OtpCode;
import com.klickit.user.entity.Role;
import com.klickit.user.entity.User;
import com.klickit.user.repository.OtpCodeRepository;
import com.klickit.user.repository.UserRepository;
import com.klickit.user.service.OtpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@DisplayName("OTP Concurrency, Row-Locking, and Security Hardening Integration Tests")
class OtpConcurrencyIntegrationTest {

    @Autowired
    private OtpService otpService;

    @Autowired
    private OtpCodeRepository otpCodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private EmailService emailService;

    @Value("${klickit.otp.secret}")
    private String otpSecret;

    private static final String TEST_EMAIL = "concurrency.test@example.com";

    @BeforeEach
    void setUp() {
        cleanUp();
        when(emailService.sendLoginCode(anyString(), anyString(), anyLong())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        transactionTemplate.execute(status -> {
            userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
            userRepository.findByEmail("nullphone1@example.com").ifPresent(userRepository::delete);
            userRepository.findByEmail("nullphone2@example.com").ifPresent(userRepository::delete);
            userRepository.findByEmail("phone.dup1@example.com").ifPresent(userRepository::delete);
            userRepository.findByEmail("phone.dup2@example.com").ifPresent(userRepository::delete);
            otpCodeRepository.findAll().stream()
                    .filter(otp -> otp.getEmail().contains("test@example.com")
                            || otp.getEmail().contains("concurrency")
                            || otp.getEmail().contains("phone"))
                    .forEach(otpCodeRepository::delete);
            return null;
        });
    }

    private String computeHmac(String email, String code) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(otpSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((email.toLowerCase().trim() + ":" + code.trim()).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Verification Row-Locking: Simultaneous verification requests allow exactly ONE consumption on PostgreSQL")
    void concurrentVerify_pessimisticRowLock_exactlyOneSucceeds() throws Exception {
        String code = "123456";
        String codeHash = computeHmac(TEST_EMAIL, code);

        // Seed an active unconsumed OTP directly in PostgreSQL
        transactionTemplate.execute(status -> {
            OtpCode otp = OtpCode.builder()
                    .email(TEST_EMAIL)
                    .codeHash(codeHash)
                    .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                    .attempts(0)
                    .build();
            return otpCodeRepository.save(otp);
        });

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    AuthResponse response = otpService.verifyOtp(TEST_EMAIL, code);
                    if (response != null && response.getToken() != null) {
                        successCount.incrementAndGet();
                    }
                } catch (InvalidOtpException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(rejectedCount.get()).isEqualTo(1);

        // Assert row state in DB: consumed exactly once
        transactionTemplate.execute(status -> {
            Optional<OtpCode> consumed = otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(TEST_EMAIL);
            assertThat(consumed).isPresent();
            assertThat(consumed.get().getConsumedAt()).isNotNull();
            assertThat(consumed.get().getAttempts()).isZero();
            return null;
        });
    }

    @Test
    @DisplayName("Request Concurrency: Simultaneous requests for the same email serialize and enforce cooldown")
    void concurrentRequests_serializedPerEmail_enforcesCooldown() throws Exception {
        int numThreads = 3;
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
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(rateLimitedCount.get()).isEqualTo(numThreads - 1);

        // Verify only 1 OTP code was saved in database
        transactionTemplate.execute(status -> {
            long count = otpCodeRepository.countByEmailAndCreatedAtAfter(TEST_EMAIL, Instant.now().minus(1, ChronoUnit.HOURS));
            assertThat(count).isEqualTo(1L);
            return null;
        });
    }

    @Test
    @DisplayName("Phone Nullability: Multiple OTP users with null phone coexist, non-null uniqueness is preserved")
    void phoneNullability_multipleNullPhonesCoexist_uniqueNonNullEnforced() {
        // 1. Two separate users with phone = null can coexist in PostgreSQL
        transactionTemplate.execute(status -> {
            User user1 = User.builder()
                    .name("User One")
                    .email("nullphone1@example.com")
                    .phone(null)
                    .password(passwordEncoder.encode("secret"))
                    .role(Role.CUSTOMER)
                    .build();
            User user2 = User.builder()
                    .name("User Two")
                    .email("nullphone2@example.com")
                    .phone(null)
                    .password(passwordEncoder.encode("secret"))
                    .role(Role.CUSTOMER)
                    .build();
            userRepository.save(user1);
            userRepository.save(user2);
            return null;
        });

        assertThat(userRepository.findByEmail("nullphone1@example.com")).isPresent();
        assertThat(userRepository.findByEmail("nullphone2@example.com")).isPresent();

        // 2. But two users with the SAME non-null phone violate unique constraint
        assertThatThrownBy(() -> {
            transactionTemplate.execute(status -> {
                User userWithPhone1 = User.builder()
                        .name("Dup Phone 1")
                        .email("phone.dup1@example.com")
                        .phone("+919876543210")
                        .password(passwordEncoder.encode("secret"))
                        .role(Role.CUSTOMER)
                        .build();
                User userWithPhone2 = User.builder()
                        .name("Dup Phone 2")
                        .email("phone.dup2@example.com")
                        .phone("+919876543210")
                        .password(passwordEncoder.encode("secret"))
                        .role(Role.CUSTOMER)
                        .build();
                userRepository.save(userWithPhone1);
                userRepository.saveAndFlush(userWithPhone2);
                return null;
            });
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
